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
exec ./mvnw spring-boot:run "$@"
