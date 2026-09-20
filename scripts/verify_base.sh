#!/usr/bin/env bash
set -euo pipefail

# M03 verification: levanta PostgreSQL, aplica migracion y seed de M01, M02 y M03,
# verifica ausencia de secretos, ejecuta los tests y emite artifacts/m03-verify.json

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

docker compose up -d postgres

echo "Waiting for PostgreSQL..."
for i in $(seq 1 60); do
  if docker compose exec -T postgres \
       psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT 1" >/dev/null 2>&1; then
    echo "PostgreSQL is ready."
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "PostgreSQL did not become ready" >&2
    docker compose logs postgres >&2
    exit 1
  fi
  sleep 1
done

docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  < db/migrations/V1__create_telemetry_contract.sql

docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  < db/seed/V2__seed_telemetry.sql

docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  < db/migrations/V3__relational_model.sql

docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  < db/seed/V4__seed_relational.sql

docker compose exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
       -v migration_pw="$MIGRATION_ROLE_PASSWORD" \
       -v writer_pw="$WRITER_ROLE_PASSWORD" \
       -v reader_pw="$READER_ROLE_PASSWORD" \
       -v operator_pw="$OPERATOR_ROLE_PASSWORD" \
  < db/migrations/V5__access_control.sql

echo "Checking for versioned secrets..."
bash scripts/check_no_secrets.sh

export POSTGRES_HOST=localhost

log=$(mktemp)
trap 'rm -f "$log"' EXIT

status="passed"
if ! mvn -B clean test > "$log" 2>&1; then
  status="failed"
fi

cat "$log"

summary=$(grep "Tests run:" "$log" | tail -1 | sed -E 's/^\[INFO\] //' || true)

mkdir -p artifacts
cat > artifacts/m03-verify.json <<EOF
{
  "command": "make verify",
  "status": "${status}",
  "tests": "${summary}"
}
EOF

if [ "$status" != "passed" ]; then
  echo "M03 verification failed" >&2
  exit 1
fi

echo "M03 verification passed"
