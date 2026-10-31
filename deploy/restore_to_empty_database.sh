#!/usr/bin/env bash
set +x
set -Eeuo pipefail
: "${TRUSTFORGE_RESTORE_DISPOSABLE_DATABASE:?Explicitly authorize an isolated restore}"
[[ "$TRUSTFORGE_RESTORE_DISPOSABLE_DATABASE" == true && "${PGDATABASE:-}" =~ ^trustforge_restore_[a-z0-9_]+$ ]] || { echo 'Restore is restricted to an explicitly authorized disposable trustforge_restore_ database.' >&2; exit 1; }
: "${PGHOST:?Set the isolated database host}"
: "${PGUSER:?Set the database identity}"
backup=${1:?Provide the private custom-format backup}
count=$(psql -X -v ON_ERROR_STOP=1 -Atc "select count(*) from information_schema.tables where table_schema='public'")
[[ "$count" == 0 ]] || { echo 'Restore target must be empty; no tables will be dropped.' >&2; exit 1; }
pg_restore --exit-on-error --no-owner --dbname="$PGDATABASE" "$backup"
echo 'Restore completed; validate counts, schema and application behavior before any operator-controlled promotion.'
