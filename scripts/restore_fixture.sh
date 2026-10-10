#!/usr/bin/env bash
set -euo pipefail

# Recuperación de fixture sintético para document_events.
# Flujo: falla controlada -> restore -> verificación -> medición (RPO/RTO/SLO).

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

FIXTURE_IDS=("RESTORE-FIX-001" "RESTORE-FIX-002" "RESTORE-FIX-003")
EXPECTED=3
SLO_SECONDS=5

count_fixture() {
  docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
    print(db.document_events.countDocuments({ eventId: { \$in: ['${FIXTURE_IDS[0]}', '${FIXTURE_IDS[1]}', '${FIXTURE_IDS[2]}'] } }));
  " | tr -d '\r'
}

echo ">> Step 1: Falla controlada (borrando fixture)"
docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  db.document_events.deleteMany({ eventId: { \$in: ['${FIXTURE_IDS[0]}', '${FIXTURE_IDS[1]}', '${FIXTURE_IDS[2]}'] } });
  print('fixture eliminado');
" > /dev/null

before=$(count_fixture)
rpo_lost=$((EXPECTED - before))

echo ">> Step 2: Restore (reinsertando fixture)"
start=$(date +%s)

docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  db.document_events.insertMany([
    { eventId: '${FIXTURE_IDS[0]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:00:00Z', payload: { note: 'fixture-1' }, schemaVersion: 2 },
    { eventId: '${FIXTURE_IDS[1]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:01:00Z', payload: { note: 'fixture-2' }, schemaVersion: 2 },
    { eventId: '${FIXTURE_IDS[2]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:02:00Z', payload: { note: 'fixture-3' }, schemaVersion: 2 }
  ]);
  print('fixture restaurado');
" > /dev/null

end=$(date +%s)
rto_seconds=$((end - start))

echo ">> Step 3: Verificación"
after=$(count_fixture)

echo ">> Step 4: Reporte"
echo "Registros esperados: ${EXPECTED}"
echo "Registros antes del restore: ${before}"
echo "Registros después del restore: ${after}"
echo "RPO (registros perdidos durante la falla): ${rpo_lost}"
echo "RTO (segundos de recuperación): ${rto_seconds}"
echo "SLO (<= ${SLO_SECONDS}s): $( [ "$rto_seconds" -le "$SLO_SECONDS" ] && echo met || echo not_met )"

if [ "$after" = "$EXPECTED" ]; then
  echo "restore: ok"
else
  echo "restore: failed" >&2
  exit 1
fi
