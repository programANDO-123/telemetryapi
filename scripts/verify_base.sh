#!/usr/bin/env bash
set -euo pipefail

# M09 verification: levanta PostgreSQL y MongoDB, aplica migraciones y seeds
# de M01 a M05, aplica schema de MongoDB, configura seguridad M09,
# prueba ciclo v1 -> v2 -> v1 -> v2, verifica ausencia de secretos,
# ejecuta tests, corre restore, ejecuta el benchmark y emite artifacts/m09-verify.json.

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

# Validate before starting containers or changing database state. Older .env files
# may be missing the administrative connection and M09 actor credentials.
required_env=(
  POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD POSTGRES_PORT
  MIGRATION_ROLE_PASSWORD WRITER_ROLE_PASSWORD READER_ROLE_PASSWORD OPERATOR_ROLE_PASSWORD
  MONGO_ROOT_USER MONGO_ROOT_PASSWORD MONGO_ROOT_URI MONGO_URI MONGO_DB
  MONGO_READER_USER MONGO_READER_PASSWORD MONGO_WRITER_USER MONGO_WRITER_PASSWORD
  MONGO_VULN_USER MONGO_VULN_PASSWORD
)
missing_env=()
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing_env+=("$name")
  fi
done
if (( ${#missing_env[@]} > 0 )); then
  printf 'Missing required env var: %s\n' "${missing_env[@]}" >&2
  echo 'Complete .env using .env.example before running make verify.' >&2
  exit 1
fi

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
  # Esperar al mongod definitivo, no al servidor temporal de docker-entrypoint.
  # ping comprueba disponibilidad; la autenticación se verifica a continuación.
  if docker compose exec -T mongo sh -c 'test "$(cat /proc/1/comm)" = mongod' >/dev/null 2>&1 &&
     docker compose exec -T mongo mongosh --host 127.0.0.1 --port 27017 --quiet admin \
       --eval 'quit(db.adminCommand({ ping: 1 }).ok === 1 ? 0 : 1)' >/dev/null 2>&1; then
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

# Recuperar el primer administrador en volúmenes sin usuarios, sin borrar datos.
# Si ya existen usuarios, se exigen las credenciales administrativas originales.
echo "Checking MongoDB administrative access..."
docker compose exec -T mongo mongosh --host 127.0.0.1 --port 27017 --quiet admin \
  --file /dev/stdin < db/mongo/bootstrap_admin.js

# --- PostgreSQL M01-M05 ---
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

# --- MongoDB schema ---
echo "Applying MongoDB schema..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/schema.js

# --- M09: Seguridad ---
echo "M09: Setting up MongoDB security..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/security_setup.js

# --- M07: ciclo de migración y rollback ---
echo "M07: Inserting v1 synthetic document..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" --eval '
  db.document_events.deleteMany({ eventId: "M07-V1-001" });
  db.document_events.insertOne({
    eventId: "M07-V1-001",
    type: "telemetry.test",
    source: "synthetic",
    timestamp: "2026-10-24T00:00:00Z",
    payload: { note: "v1" }
  });
  print("inserted v1 document");
'

echo "M07: Applying migration v1 -> v2..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/migrate_v1_to_v2.js

echo "M07: Verifying v2 document exists..."
v2count=$(docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" --eval 'print(db.document_events.countDocuments({ eventId: "M07-V1-001", schemaVersion: 2 }));' | tr -d '\r')
if [ "$v2count" != "1" ]; then
  echo "M07 ERROR: migration to v2 did not produce expected document" >&2
  exit 1
fi

echo "M07: Applying rollback v2 -> v1..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/rollback_v2_to_v1.js

echo "M07: Verifying document returned to v1..."
v1count=$(docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" --eval 'print(db.document_events.countDocuments({ eventId: "M07-V1-001", schemaVersion: { $exists: false } }));' | tr -d '\r')
if [ "$v1count" != "1" ]; then
  echo "M07 ERROR: rollback to v1 did not restore document" >&2
  exit 1
fi

echo "M07: Re-applying migration (idempotence check)..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/migrate_v1_to_v2.js

echo "M07: Cleaning up synthetic document..."
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" --eval 'db.document_events.deleteMany({ eventId: "M07-V1-001" });'

# --- Secret scan ---
echo "Checking for versioned secrets..."
bash scripts/check_no_secrets.sh

# --- Maven tests ---
export POSTGRES_HOST=localhost

log=$(mktemp)
trap 'rm -f "$log"' EXIT

status="passed"
if ! mvn -B clean test > "$log" 2>&1; then
  status="failed"
fi

cat "$log"

summary=$(grep "Tests run:" "$log" | tail -1 | sed -E 's/^\[INFO\] //' || true)

# --- M06 restore ---
echo "Running restore fixture..."
restore_status="failed"
if bash scripts/restore_fixture.sh > /dev/null 2>&1; then
  restore_status="ok"
fi

# --- M08 benchmark ---
echo "Running M08 benchmark..."
bench_status="failed"
if bash scripts/benchmark_m08.sh > /dev/null 2>&1; then
  bench_status="ok"
fi

bench_file="artifacts/m08-benchmark.json"
if [ -f "$bench_file" ]; then
  bench_json=$(cat "$bench_file")
else
  bench_json="{}"
fi

mkdir -p artifacts
cat > artifacts/m09-verify.json <<EOF
{
  "command": "make verify",
  "status": "${status}",
  "tests": "${summary}",
  "restore": "${restore_status}",
  "benchmarkStatus": "${bench_status}",
  "benchmark": ${bench_json}
}
EOF

if [ "$status" != "passed" ] || [ "$restore_status" != "ok" ] || [ "$bench_status" != "ok" ]; then
  echo "M09 verification failed" >&2
  exit 1
fi

echo "M09 verification passed"
