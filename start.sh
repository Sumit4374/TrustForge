#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
for tool in java node npm docker python3; do
  command -v "$tool" >/dev/null || { echo "Missing required tool: $tool" >&2; exit 1; }
done
if command -v mvn >/dev/null; then maven=(mvn); else maven=(./server/mvnw); fi
docker compose version >/dev/null
if [[ -n "${TRUSTFORGE_MAVEN_REPO:-}" ]]; then maven+=("-Dmaven.repo.local=$TRUSTFORGE_MAVEN_REPO"); fi
if [[ ! -f .env ]]; then
  umask 077
  python3 - <<'PY'
from pathlib import Path
import secrets
text = Path('.env.example').read_text()
for key in ('DB_PASSWORD', 'REDIS_PASSWORD', 'CACHE_REDIS_PASSWORD'):
    text = text.replace(key + '=\n', key + '=' + secrets.token_hex(24) + '\n')
Path('.env').write_text(text)
PY
  echo 'Created .env with random local datastore credentials. Configure BHARATCODE_API_KEY there for AI operations.'
fi
# .env is a trusted local shell configuration file. Do not source untrusted files.
set -a
source .env
set +a
: "${DB_PASSWORD:?Set DB_PASSWORD in .env}"
: "${REDIS_PASSWORD:?Set REDIS_PASSWORD in .env}"
: "${CACHE_REDIS_PASSWORD:?Set CACHE_REDIS_PASSWORD in .env}"
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT:-5432}/trustforge}"
python3 - <<'PY'
import socket, os, sys
import subprocess
ports = [8080, 5173]
for service, name, default in [('postgres','POSTGRES_PORT',5432),('redis-session','REDIS_PORT',6379),
                             ('redis-cache','CACHE_REDIS_PORT',6380),('mailpit','SMTP_PORT',1025)]:
    running = subprocess.check_output(['docker','compose','ps','-q','--status','running',service], text=True).strip()
    if not running:
        ports.append(int(os.environ.get(name, default)))
        if service == 'mailpit':
            ports.append(int(os.environ.get('MAILPIT_HTTP_PORT',8025)))
for port in ports:
    with socket.socket() as s:
        try:
            s.bind(('127.0.0.1', port))
        except OSError:
            sys.exit(f'Port {port} is in use. Stop its process before starting TrustForge.')
PY
backend_pid=''
frontend_pid=''
shutdown() {
  trap - EXIT INT TERM
  for pid in "$backend_pid" "$frontend_pid"; do
    if [[ -n "$pid" ]]; then
      # Each command starts in its own process group; children stop with the parent.
      kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
  docker compose stop >/dev/null 2>&1 || true
}
trap shutdown EXIT INT TERM
command -v setsid >/dev/null || { echo 'setsid is required for safe child cleanup.' >&2; exit 1; }
docker compose up -d --wait --wait-timeout 120
if [[ ! -d client/node_modules ]]; then npm --prefix client ci; fi
# Flyway runs and validates migrations as part of Spring startup before readiness.
setsid env -u DEBUG -u TRACE "${maven[@]}" -f server/pom.xml spring-boot:run '-Dspring-boot.run.arguments=--debug=false --trace=false' > /tmp/trustforge-backend.log 2>&1 &
backend_pid=$!
python3 - <<'PY'
import time, urllib.request, sys
for _ in range(120):
    try:
        with urllib.request.urlopen('http://localhost:8080/actuator/health/readiness', timeout=2) as r:
            if r.status == 200:
                break
    except Exception:
        time.sleep(1)
else:
    sys.exit('Backend readiness timed out. Inspect /tmp/trustforge-backend.log (do not share secrets).')
PY
setsid npm --prefix client run dev -- --host 127.0.0.1 --port 5173 --strictPort > /tmp/trustforge-frontend.log 2>&1 &
frontend_pid=$!
echo 'TrustForge: http://localhost:5173'
echo "Verification email inbox: http://localhost:${MAILPIT_HTTP_PORT:-8025}"
echo 'Logs: /tmp/trustforge-backend.log and /tmp/trustforge-frontend.log'
echo 'Ctrl+C stops application processes and supporting services; volumes are preserved.'
wait -n "$backend_pid" "$frontend_pid"
