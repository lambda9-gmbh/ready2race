#!/bin/sh
# Builds are deliberately separate: this checks installed images, startup and isolation.
set -eu
cd "$(dirname "$0")/.."
smoke_directory=$(mktemp -d)
smoke_project="r2r-smoke-$$"
smoke_project_b="${smoke_project}-b"
smoke_phase=initialization

compose() {
    docker compose --env-file "$smoke_directory/instance.env" -p "$smoke_project" -f compose.yaml "$@"
}
compose_b() {
    docker compose --env-file "$smoke_directory/instance-b.env" -p "$smoke_project_b" -f compose.yaml "$@"
}
cleanup() {
    result=$?
    if [ "$result" -ne 0 ]; then
        printf 'Docker smoke test failed during %s.\n' "$smoke_phase" >&2
        compose logs --tail=80 || true
        compose_b logs --tail=80 || true
    fi
    compose down -v --remove-orphans >/dev/null 2>&1 || true
    compose_b down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf "$smoke_directory"
}
trap cleanup EXIT
cat > "$smoke_directory/instance.env" <<'EOF'
BACKEND_IMAGE=ready2race-backend
FRONTEND_IMAGE=ready2race-frontend
BACKEND_VERSION=local
FRONTEND_VERSION=local
HTTP_PORT=0
DATABASE_PASSWORD=smoke-database-only
ADMIN_EMAIL=smoke@example.org
ADMIN_PASSWORD=smoke-admin-only-123!
SECURITY_PEPPER=smoke-pepper-only
SMTP_HOST=127.0.0.1
SMTP_PORT=1025
SMTP_USER=smoke
SMTP_PASSWORD=smoke
SMTP_FROM_ADDRESS=smoke@example.org
CORS_ALLOWED_ORIGINS=https://club.example.org
API_BASE_URL=https://race.example.org/api
FONTS_URL=https://race.example.org/static/fonts
EOF
sed 's/race.example.org/other-race.example.org/g' "$smoke_directory/instance.env" > "$smoke_directory/instance-b.env"

smoke_phase=startup
compose up -d --wait --wait-timeout 240
compose_b up -d --wait --wait-timeout 240
smoke_address="http://$(compose port frontend 80)"
smoke_address_b="http://$(compose_b port frontend 80)"
smoke_phase=frontend-configuration
curl -fsS "$smoke_address/app/" | grep -q 'runtime-config.js'
curl -fsS "$smoke_address/runtime-config.js" > "$smoke_directory/runtime-config.js"
node - "$smoke_directory/runtime-config.js" <<'EOF'
const fs = require('node:fs');
const vm = require('node:vm');
const context = {window: {}};
vm.runInNewContext(fs.readFileSync(process.argv[2], 'utf8'), context);
if (context.window.READY2RACE_CONFIG.apiBaseUrl !== 'https://race.example.org/api' ||
    context.window.READY2RACE_CONFIG.fontsUrl !== 'https://race.example.org/static/fonts') {
    throw new Error('Absolute runtime configuration was not preserved');
}
EOF
curl -fsS "$smoke_address_b/runtime-config.js" | grep -q 'https://other-race.example.org/api'
smoke_phase=cross-origin-api
curl -fsS -X OPTIONS "$smoke_address/api/login" \
    -H 'Origin: https://club.example.org' \
    -H 'Access-Control-Request-Method: PUT' \
    -H 'Access-Control-Request-Headers: content-type,x-api-session' \
    -D "$smoke_directory/cors-headers" -o /dev/null
grep -qi 'Access-Control-Allow-Origin: https://club.example.org' "$smoke_directory/cors-headers"
rejected_status=$(curl -s -o /dev/null -w '%{http_code}' "$smoke_address/api/login" \
    -H 'Origin: https://other.example.org')
test "$rejected_status" = 403
curl -fsS "$smoke_address/api/login" -H 'Origin: https://club.example.org' \
    -H 'Content-Type: application/json' \
    --data '{"email":"smoke@example.org","password":"smoke-admin-only-123!"}' \
    -D "$smoke_directory/login-headers" -o /dev/null
grep -qi '^X-Api-Session:' "$smoke_directory/login-headers"

# Files and DB content must not cross the project boundary.
smoke_phase=instance-isolation
compose exec -T backend sh -c 'printf isolation > /data/static/smoke.txt'
compose_b exec -T backend test ! -e /data/static/smoke.txt
curl -fsS "$smoke_address/static/smoke.txt" | grep -q isolation
compose exec -T db psql -U ready2race -d ready2race -c 'CREATE TABLE public.smoke_isolation (id integer)' >/dev/null
table_b=$(compose_b exec -T db psql -U ready2race -d ready2race -Atc "SELECT to_regclass('public.smoke_isolation') IS NULL")
test "$table_b" = t

# Restarting the application preserves uploaded files and database data.
smoke_phase=persistence
compose restart backend
compose up -d --wait --wait-timeout 240
compose exec -T backend test -f /data/static/smoke.txt
table_a=$(compose exec -T db psql -U ready2race -d ready2race -Atc "SELECT to_regclass('public.smoke_isolation') IS NOT NULL")
test "$table_a" = t
printf 'Docker smoke test passed: startup, runtime config, CORS, persistence and instance isolation.\n'
