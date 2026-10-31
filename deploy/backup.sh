#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077
: "${PGHOST:?Set a private database host}"
: "${PGDATABASE:?Set the source database}"
: "${PGUSER:?Set the database identity}"
: "${PGPASSFILE:?Use a protected password file or your managed credential adapter}"
[[ "$(stat -c %a "$PGPASSFILE")" == 600 ]] || { echo 'PGPASSFILE must have mode0600.' >&2; exit 1; }
output=${1:?Provide a new private backup path}
[[ ! -e "$output" ]] || { echo 'Refusing to replace an existing backup.' >&2; exit 1; }
pg_dump --format=custom --no-owner --file="$output"
echo 'Backup created. Verify checksums, retention, encryption and an isolated restore before relying on it.'
