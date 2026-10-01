#!/bin/bash
# Arranque del microservicio Spring Boot (servicio-jpa, puerto 8082).
#
# MOTIVO DE ESTE SCRIPT
# application.properties NO lleva la conexion por defecto: las tres variables
# SPRING_DATASOURCE_* son obligatorias. Si faltara alguna, Spring arrancaria y
# fallaria en la primera consulta, y ademas la password de Neon quedaria escrita
# en un archivo que sube el profesor y el resto del equipo.
#
# La JVM tampoco lee el .env. Este script toma la credencial del .env de Django
# (projecto_formativo/.env) y la traduce al formato JDBC, de modo que
# Spring y Django apuntan a la MISMA base sin duplicar el secreto en dos sitios.
#
# Segundo motivo: el servicio se autentica contra Django con el header
# X-Internal-Token, cuyo valor sale de INTERNAL_API_TOKEN. Sin exportarlo,
# application.properties resuelve app.internal.token a vacio, Django responde 401
# y el fail-safe de InterServiceClient bloquea TODA purga fisica con un 400
# confuso ("existen ordenes con este producto") aunque el producto no tenga
# ninguna.
#
# Uso:  ./start.sh [args_extra para spring-boot:run]

set -euo pipefail

RAIZ_JAVA="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULO="$RAIZ_JAVA/servicio"
ENV_DJANGO="${DJANGO_ENV_FILE:-$RAIZ_JAVA/../projecto_formativo/.env}"

# ── Variables del backend Django ────────────────────────────────────────────
# Prioridad: si ya estan en el entorno (CI, produccion, docker) no se tocan;
# solo se cargan del .env como fallback para desarrollo local.
leer_del_env() {
    local clave="$1"
    local valor
    valor="$(sed -n "s/^${clave}=//p" "$ENV_DJANGO" | tail -n 1)"
    # Quitar comillas envolventes si las hay
    valor="${valor%\"}"; valor="${valor#\"}"
    valor="${valor%\'}"; valor="${valor#\'}"
    printf '%s' "$valor"
}

if [ ! -f "$ENV_DJANGO" ]; then
    echo "ERROR: no se encontro el .env de Django en $ENV_DJANGO" >&2
    echo "      .Override con DJANGO_ENV_FILE=/ruta/al/.env" >&2
    exit 1
fi

for clave in INTERNAL_API_TOKEN DJANGO_BASE_URL; do
    valor_env="${!clave:-}"
    if [ -z "$valor_env" ]; then
        valor_env="$(leer_del_env "$clave")"
        if [ -n "$valor_env" ]; then
            export "$clave=$valor_env"
        fi
    fi
done

# ── Credenciales de MongoDB ──────────────────────────────────────────────────
# application.properties lee ${SPRING_MONGODB_URI}. Si la variable no
# llega a estar definida, Spring Boot NO falla: usa su valor por defecto
# mongodb://localhost:27017/test. Ese silencio es peligroso, porque el
# mongod local no exige autenticacion y el servicio empieza a guardar el
# catalogo ahi, creyendo que escribe en Atlas. Por eso aqui se cablea la URI
# y, si de verdad no se encuentra ninguna, se aborta.
#
# Orden de precedencia:
#   1. SPRING_MONGODB_URI ya exportada en el entorno
#   2. SPRING_MONGODB_URI del .env de Django
#   3. MONGODB_URI del .env de Django (se le cambia la base a la de Spring)
if [ -z "${SPRING_MONGODB_URI:-}" ]; then
    SPRING_MONGODB_URI="$(leer_del_env SPRING_MONGODB_URI)"
fi
if [ -z "${SPRING_MONGODB_URI:-}" ]; then
    mongo_uri_django="$(leer_del_env MONGODB_URI)"
    if [ -n "$mongo_uri_django" ]; then
        # Django usa la base 'projecto_formativo' y Spring la 'proyecto_formativo'.
        # Se reapunta conservando usuario, credenciales, host y parametros.
        #
        # El orden importa: primero se separa la query, porque si se hiciera al
        # reves el corte "${uri%/*}" se llevaria por delante el "?..." (va detras
        # de la barra) y retryWrites/w=majority se perderian en silencio.
        case "$mongo_uri_django" in
            *\?*)
                base_query="${mongo_uri_django%%\?*}"
                params="${mongo_uri_django#*\?}"
                ;;
            *)
                base_query="$mongo_uri_django"
                params=""
                ;;
        esac
        sin_base="${base_query%/*}"
        SPRING_MONGODB_URI="${sin_base}/${MONGO_DB_PRODUCTOS:-proyecto_formativo}"
        if [ -n "$params" ]; then
            SPRING_MONGODB_URI="${SPRING_MONGODB_URI}?${params}"
        fi
    fi
fi

if [ -z "${SPRING_MONGODB_URI:-}" ]; then
    echo "ERROR: no se encontro SPRING_MONGODB_URI ni MONGODB_URI en $ENV_DJANGO" >&2
    echo "      Sin esto Spring arranca contra mongodb://localhost:27017/test," >&2
    echo "      sin autenticacion, y el catalogo se guardaria en el sitio equivocado." >&2
    echo "      Define SPRING_MONGODB_URI en el .env o exportala antes." >&2
    exit 1
fi
export SPRING_MONGODB_URI

# Solo el host, para no filtrar la contrasena ni al log ni a la consola.
mongo_host="$(printf '%s' "$SPRING_MONGODB_URI" | sed -E 's|.*://[^@]*@||; s|[:/].*||')"
echo "OK: SPRING_MONGODB_URI -> host=$mongo_host base=${MONGO_DB_PRODUCTOS:-proyecto_formativo}"

# ── Materializacion de la configuracion local ─────────────────────────────────
# Measured en este proyecto: la JVM que arranca spring-boot:run no recibe de
# forma fiable el entorno heredado (se comprobo con /proc/<pid>/environ), y el
# namespace spring.data.mongodb.* de Spring Boot 3 esta en 4.1.1 deprecado con
# nivel "error", de modo que escribir ahi no da error: se ignora y Spring cae
# en mongodb://127.0.0.1:27017/test sin credenciales.
#
# Para no depender de ninguna de esas dos cosas, la URI se escribe en un
# archivo de propiedades externo y se le pasa a Spring con
# spring.config.additional-location. Esa fuente tiene precedencia sobre
# application.properties y sobre las variables de entorno, y como la ruta no
# lleva ningun secreto, no queda la contrasena expuesta en `ps`.
PROPS_LOCAL="$MODULO/config-local.properties"
umask 077
{
    echo "# Generado por start.sh. NO versionar: contiene la URI con credenciales."
    echo "spring.mongodb.uri=$SPRING_MONGODB_URI"
} > "$PROPS_LOCAL"
chmod 600 "$PROPS_LOCAL"
echo "OK: $PROPS_LOCAL (600, ignorado por git)"

# ── Credenciales de PostgreSQL ───────────────────────────────────────────────
# Django se conecta con DATABASE_URL (formato libpq); JDBC no entiende ese
# esquema. Se traduce: postgresql://user:pass@host/db?sslmode=require
#   -> jdbc:postgresql://host/db?sslmode=require
# El usuario y la password se leen de la propia URL, que es lo unico que
# evita volver a escribirlas a mano (y volver a filtrarlas en un commit).
if [ -z "${SPRING_DATASOURCE_URL:-}" ] && [ -z "${DATABASE_URL:-}" ]; then
    DATABASE_URL="$(leer_del_env DATABASE_URL)"
fi
if [ -n "${DATABASE_URL:-}" ] && [ -z "${SPRING_DATASOURCE_URL:-}" ]; then
    sin_esquema="${DATABASE_URL#*://}"
    usuario="${sin_esquema%%:*}"
    resto="${sin_esquema#*:}"
    password="${resto%%@*}"
    host_resto="${resto#*@}"
    host="${host_resto%%/*}"
    base_query="${host_resto#*/}"                 # db?sslmode=require
    base="${base_query%%\?*}"
    query="${base_query#*\?}"
    if [ -z "$query" ]; then query="sslmode=require"; fi

    export SPRING_DATASOURCE_URL="jdbc:postgresql://${host}/${base}?${query}"
    export SPRING_DATASOURCE_USERNAME="$usuario"
    export SPRING_DATASOURCE_PASSWORD="$password"
    echo "OK: SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL} (traducido de DATABASE_URL)"
fi

for clave in SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD; do
    if [ -z "${!clave:-}" ]; then
        echo "ERROR: falta $clave y no se pudo derivar de DATABASE_URL" >&2
        echo "      Exporta las tres, o defines DATABASE_URL en el .env de Django." >&2
        exit 1
    fi
done

# ── Verificacion fail-fast ──────────────────────────────────────────────────
# El servicio arranca igual sin token (los endpoints de solo lectura degradan a
# vacio), pero avisamos aqui para que el fallo se vea en consola al arrancar y
# no tres meses despues dentro de un 400 de purga.
if [ -z "${INTERNAL_API_TOKEN:-}" ]; then
    echo "WARN: INTERNAL_API_TOKEN vacio. Las purgas fisicas fallaran con 400" >&2
    echo "      (fail-safe: Django respondera 401 al header X-Internal-Token)." >&2
else
    echo "OK: INTERNAL_API_TOKEN cargado desde $ENV_DJANGO"
fi
echo "OK: DJANGO_BASE_URL=${DJANGO_BASE_URL:-http://127.0.0.1:8000}"

# ── Arranque ────────────────────────────────────────────────────────────────
cd "$MODULO"

# config-local.properties lleva la URI de MongoDB. Se pasa como
# spring.config.additional-location, que Spring Boot procesa antes de cargar la
# configuracion, asi que gana a application.properties.
#
# Los argumentos que llegan a este script se reenvian a Spring, no a Maven.
# Antes se hacia exec ./mvnw spring-boot:run "$@", y Maven interpretaba
# "--server.port=..." como opcion suya e imprimia su ayuda sin arrancar nada.
RUN_ARGS="--spring.config.additional-location=file:./config-local.properties"
if [ "$#" -gt 0 ]; then
    RUN_ARGS="$RUN_ARGS $*"
fi

exec ./mvnw -Dspring-boot.run.arguments="$RUN_ARGS" spring-boot:run