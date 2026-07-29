#!/bin/bash
#
# Creates the additional per-service databases required by ADR-002
# (database-per-service) on first initialization of the Postgres volume.
# Mirrors infrastructure/docker/init-scripts/01-create-multiple-databases.sh
# exactly — kept as a separate copy (rather than referenced across the
# docker/ and kubernetes/ trees) so the Kustomize base stays self-contained
# and cloud-agnostic. Mounted into /docker-entrypoint-initdb.d via the
# postgres-init ConfigMap (see kustomization.yaml).
#
set -euo pipefail

# Creates one database, owned by the main POSTGRES_USER.
# Idempotent via \gexec so re-running against an existing database is safe.
#
# $1 — the database name to create
create_database() {
    local database="$1"
    echo "  init-db: creating database '${database}'"

    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
        SELECT 'CREATE DATABASE ${database}'
        WHERE NOT EXISTS (
            SELECT FROM pg_database WHERE datname = '${database}'
        )\gexec
EOSQL

    # GRANT is a separate statement because it must run after the database
    # exists; \gexec above only conditionally emits the CREATE.
    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
        -c "GRANT ALL PRIVILEGES ON DATABASE ${database} TO ${POSTGRES_USER};"
}

if [ -n "${POSTGRES_MULTIPLE_DATABASES:-}" ]; then
    echo "init-db: additional databases requested: ${POSTGRES_MULTIPLE_DATABASES}"

    # Comma-separated list -> whitespace-separated for word splitting.
    for db in $(echo "${POSTGRES_MULTIPLE_DATABASES}" | tr ',' ' '); do
        create_database "${db}"
    done

    echo "init-db: additional databases ready"
else
    echo "init-db: POSTGRES_MULTIPLE_DATABASES unset — nothing extra to create"
fi
