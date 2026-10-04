#!/bin/sh
set -eu

if [ -n "${DATABASE_URL:-}" ]; then
    DB_URL="${DATABASE_URL#postgresql://}"
    DB_URL="${DB_URL#postgres://}"

    DB_CREDENTIALS="${DB_URL%%@*}"
    DB_HOST_AND_DATABASE="${DB_URL#*@}"

    DB_USER="${DB_CREDENTIALS%%:*}"
    DB_PASSWORD="${DB_CREDENTIALS#*:}"

    DB_HOSTPORT="${DB_HOST_AND_DATABASE%%/*}"
    DB_DATABASE="${DB_HOST_AND_DATABASE#*/}"

    export SPRING_DATASOURCE_URL="jdbc:postgresql://${DB_HOSTPORT}/${DB_DATABASE}"
    export SPRING_DATASOURCE_USERNAME="${DB_USER}"
    export SPRING_DATASOURCE_PASSWORD="${DB_PASSWORD}"
fi

if [ -n "${REDIS_URL:-}" ]; then
    export SPRING_DATA_REDIS_URL="${REDIS_URL}"
fi

if [ -n "${PORT:-}" ]; then
    export SERVER_PORT="${PORT}"
fi

exec java -jar /app/app.jar