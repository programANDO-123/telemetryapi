// Rollback idempotente v2 a v1.
// Elimina los campos v2 y el schemaVersion para regresar el documento a v1
// Correr dos veces no duplica ni daña ls datos

const result = db.document_events.updateMany(
  { schemaVersion: 2 },
  {
    $unset: {
      schemaVersion: "",
      firmwareVersion: "",
      qualityScore: ""
    }
  }
);

print("Matched: " + result.matchedCount);
print("Modified: " + result.modifiedCount);
print("Rollback v2 to v1 completed");
