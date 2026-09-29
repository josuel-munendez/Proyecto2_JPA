#!/bin/bash
# Arranque del microservicio Spring Boot (servicio-jpa, puerto 8082).
#
# MOTIVO DE ESTE SCRIPT
# El servicio se autentica contra Django con el header X-Internal-Token, cuyo
# valor sale de INTERNAL_API_TOKEN. Ese valor vive en el .env del backend Django
# (projecto_formativo/.env), que la JVM no lee: sin exportarlo, application.properties
# resuelve app.internal.token a vacio, Django responde 401 y el fail-safe de
# InterServiceClient bloquea TODA purga fisica con un 400 confuso
# ("existen ordenes con este producto") aunque el producto no tenga ninguna.
# Este script lee el .env de Django y exporta la variable antes de arrancar.
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
