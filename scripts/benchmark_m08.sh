#!/usr/bin/env bash
set -euo pipefail

#Benchmark: compara paginacion por offset vs cursor.
# Inserta 10k fixtures, corre 20 iteraciones y emite p50/p95.

test -f .env || { echo "missing .env file" >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

mkdir -p artifacts

echo "Running M08 benchmark..."

docker compose exec -T mongo mongosh --quiet "$MONGO_DB" --eval '
  db.document_events.deleteMany({ type: "benchmark" });

  let docs = [];
  let baseTime = Date.now();
  for (let i = 0; i < 10000; i++) {
    docs.push({
      eventId: "BENCH-" + i,
      type: "benchmark",
      source: "load-test",
      timestamp: new Date(baseTime + i * 1000).toISOString(),
      payload: { i: i },
      schemaVersion: 2
    });
  }
  db.document_events.insertMany(docs);
  print("Inserted 10000 benchmark documents");

  let offsetTimes = [];
  for (let i = 0; i < 20; i++) {
    let start = Date.now();
    db.document_events.find({ type: "benchmark" }).sort({ timestamp: 1 }).skip(5000).limit(100).toArray();
    offsetTimes.push(Date.now() - start);
  }

  let cursorDoc = db.document_events.find({ type: "benchmark" }).sort({ timestamp: 1 }).skip(5000).limit(1).next();
  let cursorTimes = [];
  for (let i = 0; i < 20; i++) {
    let start = Date.now();
    db.document_events.find({ type: "benchmark", timestamp: { $gt: cursorDoc.timestamp } }).sort({ timestamp: 1 }).limit(100).toArray();
    cursorTimes.push(Date.now() - start);
  }

  offsetTimes.sort((a,b)=>a-b);
  cursorTimes.sort((a,b)=>a-b);

  let result = {
    sample: 20,
    fixtures: 10000,
    environment: "docker-compose local",
    offset: {
      p50: offsetTimes[Math.floor(20 * 0.5)],
      p95: offsetTimes[Math.floor(20 * 0.95)]
    },
    cursor: {
      p50: cursorTimes[Math.floor(20 * 0.5)],
      p95: cursorTimes[Math.floor(20 * 0.95)]
    }
  };

  print("---BENCHMARK_JSON---");
  print(JSON.stringify(result));
  print("---END_BENCHMARK_JSON---");
' > /tmp/m08_bench_raw.txt

# Extraer el JSON delimitado
sed -n '/---BENCHMARK_JSON---/,/---END_BENCHMARK_JSON---/p' /tmp/m08_bench_raw.txt | sed '1d;$d' > artifacts/m08-benchmark.json

cat artifacts/m08-benchmark.json
echo "Benchmark completed"
