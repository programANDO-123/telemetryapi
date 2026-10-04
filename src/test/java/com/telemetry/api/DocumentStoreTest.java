package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

class DocumentStoreTest {

    private static final String COLLECTION = "document_events";

    private static MongoClient client;
    private static MongoCollection<Document> events;

    @BeforeAll
    static void setUp() {
        String uri = requireEnv("MONGO_URI");
        String db = requireEnv("MONGO_DB");

        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(db);
        events = database.getCollection(COLLECTION);

        events.deleteMany(new Document());
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Happy path: insertar y consultar evento valido por eventId")
    void happyPath() {
        String eventId = "M05-HAPPY-001";

        Document event = new Document()
                .append("eventId", eventId)
                .append("type", "telemetry.created")
                .append("source", "GPS-001")
                .append("timestamp", "2026-10-03T20:00:00Z")
                .append("payload", new Document()
                        .append("speedKmh", 80)
                        .append("distanceKm", 120.5));

        events.insertOne(event);

        Document found = events.find(new Document("eventId", eventId)).first();

        assertNotNull(found);
        assertEquals(eventId, found.getString("eventId"));
        assertEquals("telemetry.created", found.getString("type"));
        assertEquals("GPS-001", found.getString("source"));
    }

    @Test
    @DisplayName("Duplicado: mismo eventId no se inserta dos veces")
    void duplicado() {
        String eventId = "M05-DUPLICADO-001";

        Document event = new Document()
                .append("eventId", eventId)
                .append("type", "telemetry.created")
                .append("source", "GPS-001")
                .append("timestamp", "2026-10-03T20:01:00Z")
                .append("payload", new Document()
                        .append("speedKmh", 70)
                        .append("distanceKm", 100.0));

        events.insertOne(event);

        assertThrows(MongoWriteException.class, () -> events.insertOne(event));

        long count = events.countDocuments(new Document("eventId", eventId));

        assertEquals(1, count);
    }

    @Test
    @DisplayName("Ausencia: consultar eventId inexistente devuelve cero")
    void ausencia() {
        String eventId = "M05-NO-EXISTE-001";

        long count = events.countDocuments(
                new Document("eventId", eventId)
        );

        assertEquals(0, count);
    }

    @Test
    @DisplayName("Fallo declarado: documento sin campo requerido es rechazado")
    void falloDeclarado() {
        Document invalidEvent = new Document()
                .append("eventId", "M05-INVALID-001")
                .append("type", "telemetry.created")
                .append("source", "GPS-001")
                .append("timestamp", "2026-10-03T20:02:00Z");
        
        assertThrows(MongoWriteException.class, () -> events.insertOne(invalidEvent));

        long count = events.countDocuments(
                new Document("eventId", "M05-INVALID-001")
        );

        assertEquals(0, count);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}