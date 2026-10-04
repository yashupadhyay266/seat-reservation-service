#!/bin/sh
set -eu

if [ -n "${DATABASE_URL:-}" ]; then
    case "$DATABASE_URL" in
        jdbc:postgresql://*)
            export SPRING_DATASOURCE_URL="$DATABASE_URL"
            ;;
        postgresql://*)
            export SPRING_DATASOURCE_URL="jdbc:${DATABASE_URL}"
            ;;
        postgres://*)
            export SPRING_DATASOURCE_URL="jdbc:postgresql://${DATABASE_URL#postgres://}"
            ;;
    esac
fi

if [ -n "${REDIS_URL:-}" ]; then
    export SPRING_DATA_REDIS_URL="$REDIS_URL"
fi

if [ -n "${PORT:-}" ]; then
    export SERVER_PORT="$PORT"
fi

exec java -jar /app/app.jar