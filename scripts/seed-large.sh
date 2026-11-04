#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Isolated database: never seed millions of historical rows into the app database.
mysql_exec() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 "$@"' sh "$@"; }
mysql_exec -e "CREATE DATABASE mta_benchmark CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql_exec mta_benchmark < backend/src/main/resources/db/migration/V1__schema.sql
mysql_exec mta_benchmark < backend/src/main/resources/db/migration/V2__demo_catalog.sql
mysql_exec mta_benchmark < database/seed_large.sql
