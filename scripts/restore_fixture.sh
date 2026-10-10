#!/usr/bin/env bash
set -euo pipefail

# Recuperación de fixture sintético para document_events.
# Flujo: falla controlada -> restore -> verificación -> medición.

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

FIXTURE_IDS=("RESTORE-FIX-001" "RESTORE-FIX-002" "RESTORE-FIX-003")

echo ">> Step 1: Falla controlada (borrando fixture)"
docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  db.document_events.deleteMany({ eventId: { \$in: ['${FIXTURE_IDS[0]}', '${FIXTURE_IDS[1]}', '${FIXTURE_IDS[2]}'] } });
  print('fixture eliminado');
"

remaining=$(docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  print(db.document_events.countDocuments({ eventId: { \$in: ['${FIXTURE_IDS[0]}', '${FIXTURE_IDS[1]}', '${FIXTURE_IDS[2]}'] } }));
" | tr -d '\r')

echo ">> Step 2: Restore (reinsertando fixture)"
start=$(date +%s)

docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  db.document_events.insertMany([
    { eventId: '${FIXTURE_IDS[0]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:00:00Z', payload: { note: 'fixture-1' }, schemaVersion: 2 },
    { eventId: '${FIXTURE_IDS[1]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:01:00Z', payload: { note: 'fixture-2' }, schemaVersion: 2 },
    { eventId: '${FIXTURE_IDS[2]}', type: 'restore.test', source: 'restore-script', timestamp: '2026-10-09T00:02:00Z', payload: { note: 'fixture-3' }, schemaVersion: 2 }
  ]);
  print('fixture restaurado');
"

end=$(date +%s)
elapsed=$((end - start))

echo ">> Step 3: Verificación"
restored=$(docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval "
  print(db.document_events.countDocuments({ eventId: { \$in: ['${FIXTURE_IDS[0]}', '${FIXTURE_IDS[1]}', '${FIXTURE_IDS[2]}'] } }));
" | tr -d '\r')

echo ">> Step 4: Reporte"
echo "Documentos antes del restore: ${remaining}"
echo "Documentos después del restore: ${restored}"
echo "Tiempo de recuperación: ${elapsed}s"

if [ "$restored" = "3" ]; then
  echo "restore: ok"
else
  echo "restore: failed" >&2
  exit 1
fi
