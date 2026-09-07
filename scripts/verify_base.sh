#!/usr/bin/env bash
set -euo pipefail

required_files=(
  ".env.example"
  "Makefile"
  "docker-compose.yml"
  "docs/ADR-000-starter-base.md"
  "docs/ADR-001-telemetry-schema.md"
  "db/migrations/V1__create_telemetry_contract.sql"
  "evidence/m01-data-contract.json"
  ".github/workflows/cdrl-feedback.yml"
)

for required in "${required_files[@]}"; do
  test -f "$required" || { echo "missing required file: $required" >&2; exit 1; }
done

command -v docker >/dev/null 2>&1 || {
  echo "docker is required to verify the M01 migration" >&2
  exit 1
}

docker compose config --quiet
docker compose up -d postgres

for attempt in {1..30}; do
  if docker compose exec -T postgres sh -lc 'pg_isready -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"'; then
    break
  fi

  if [ "$attempt" -eq 30 ]; then
    echo "PostgreSQL did not become ready" >&2
    exit 1
  fi

  sleep 1
done

docker compose exec -T postgres sh -lc 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < db/migrations/V1__create_telemetry_contract.sql

python3 - <<'PY'
import json
from pathlib import Path

payload = json.loads(Path("evidence/m01-data-contract.json").read_text())
required = {"assignmentId", "commitSha", "commands", "results", "assumptions", "limitations"}
missing = sorted(required.difference(payload))
if missing:
    raise SystemExit(f"missing evidence fields: {', '.join(missing)}")
PY

mkdir -p artifacts
python3 - <<'PY'
import json
from pathlib import Path

Path("artifacts/base-verify.json").write_text(json.dumps({
    "status": "starter_base_valid",
    "scope": "structure_and_contract_only",
    "nextMilestone": "m01-data-contract"
}, indent=2) + "\n")
PY
mvn clean test
echo "CDRL starter base verification passed"
