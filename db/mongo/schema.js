// Schema e indices del almacen documental de eventos (M05).
// Se aplica sobre la base ya seleccionada por mongosh.

const validator = {
  $jsonSchema: {
    bsonType: "object",
    required: ["eventId", "type", "source", "timestamp", "payload"],
    properties: {
      eventId:   { bsonType: "string" },
      type:      { bsonType: "string" },
      source:    { bsonType: "string" },
      timestamp: { bsonType: "string" },
      payload:   { bsonType: "object" },
      metadata:  {
        bsonType: "object",
        properties: {
          schemaVersion: { bsonType: "int" },
          receivedBy:    { bsonType: "string" }
        }
      }
    }
  }
};

const existing = db.getCollectionNames();

if (existing.includes("document_events")) {
  db.runCommand({
    collMod: "document_events",
    validator: validator,
    validationLevel: "strict",
    validationAction: "error"
  });
} else {
  db.createCollection("document_events", {
    validator: validator,
    validationLevel: "strict",
    validationAction: "error"
  });
}

// Indices declarados para las consultas del ADR.
db.document_events.createIndex({ eventId: 1 }, { unique: true });
db.document_events.createIndex({ type: 1, timestamp: -1 });
db.document_events.createIndex({ source: 1, timestamp: -1 });

print("Schema and indexes applied to document_events collection");
