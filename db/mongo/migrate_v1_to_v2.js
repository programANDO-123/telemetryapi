// Migracion idempotente v1 --- v2.
// Agrega schemaVersion=2 a los documentos que no lo tengan.
// Correr dos veces no duplica ni daña datos.

const result = db.document_events.updateMany(
  { schemaVersion: { $exists: false } },
  { $set: { schemaVersion: 2 } }
);

print("Matched: " + result.matchedCount);
print("Modified: " + result.modifiedCount);
print("Migration v1 to v2 completed");
