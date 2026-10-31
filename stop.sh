#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
# The start.sh process owns the host app processes; stop it with Ctrl+C.
docker compose stop
