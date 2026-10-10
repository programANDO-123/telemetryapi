#!/usr/bin/env bash
set -euo pipefail

# M06 verification: levanta PostgreSQL y MongoDB, aplica migraciones y seeds
# de M01 a M05, aplica el schema de MongoDB, corre la migración v1->v2,
# verifica ausencia de secretos, ejecuta los tests, corre el restore de
# fixture y emite artifacts/m06-verify.json.

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

docker compose up -d postgres mongo

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

echo "Waiting for MongoDB..."
for i in $(seq 1 60); do
  if docker compose exec -T mongo \
       mongosh --quiet --eval "db.adminCommand('ping').ok" >/dev/null 2>&1; then
    echo "MongoDB is ready."
    break
  fi
  if [ "$i" -eq 60 ]; then
    echo "MongoDB did not become ready" >&2
    docker compose logs mongo >&2
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

echo "Applying MongoDB schema..."
docker compose exec -T mongo \
  mongosh --quiet "$MONGO_DB" \
  < db/mongo/schema.js

echo "Applying MongoDB migration v1 -> v2..."
docker compose exec -T mongo \
  mongosh --quiet "$MONGO_DB" \
  < db/mongo/migrate_v1_to_v2.js

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

echo "Running restore fixture..."
restore_status="failed"
if bash scripts/restore_fixture.sh > /dev/null 2>&1; then
  restore_status="ok"
fi

mkdir -p artifacts
cat > artifacts/m06-verify.json <<EOF
{
  "command": "make verify",
  "status": "${status}",
  "tests": "${summary}",
  "restore": "${restore_status}"
}
EOF

if [ "$status" != "passed" ] || [ "$restore_status" != "ok" ]; then
  echo "M06 verification failed" >&2
  exit 1
fi

echo "M06 verification passed"
