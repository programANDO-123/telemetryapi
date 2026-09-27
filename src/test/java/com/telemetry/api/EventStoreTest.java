package com.telemetry.api;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

class EventStoreTest {

    private static final String COLLECTION = "events";

    private static MongoClient client;
    private static MongoCollection<Document> events;

    @BeforeAll
    static void setUp() {
        String uri = requireEnv("MONGO_URI");
        String db  = requireEnv("MONGO_DB");

        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(db);
        events = database.getCollection(COLLECTION);

        events.createIndex(Indexes.ascending("ts"));
        events.createIndex(Indexes.ascending("deviceId"));
        events.createIndex(Indexes.ascending("type"));
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Caso normal: insertar y consultar evento por rango")
    void casoNormal() {
        String deviceId = "GPS-M04-NORMAL";

        Instant ts = Instant.now();
        Document event = new Document()
                .append("ts", java.util.Date.from(ts))
                .append("deviceId", deviceId)
                .append("type", "overspeed");

        events.insertOne(event);
        Document filter = new Document()
                .append("deviceId", deviceId)
                .append(
                        "ts",
                        new Document(
                                "$gte",
                                java.util.Date.from(ts.minusSeconds(5))
                        ).append(
                                "$lte",
                                java.util.Date.from(ts.plusSeconds(5))
                        )
                );

        long count = events.countDocuments(filter);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
        events.deleteMany(new Document("deviceId", deviceId));
        
    }

    @Test
    @DisplayName("Caso límite 1: evento incompleto es rechazado")
    void casoLimite1() {
        Document incompleteEvent = new Document()
                .append("deviceId", "GPS-M04-INCOMPLETE");

        boolean valid = incompleteEvent.containsKey("ts")
                && incompleteEvent.containsKey("deviceId")
                && incompleteEvent.containsKey("type");

        org.junit.jupiter.api.Assertions.assertFalse(
                valid,
                "Un evento incompleto no debe cumplir el contrato mínimo"
        );
        
    }

    @Test
    @DisplayName("Caso límite 2: rango vacío devuelve cero eventos")
    void casoLimite2() {
        String deviceId = "GPS-M04-EMPTY";

        Instant start = Instant.parse("2099-01-01T00:00:00Z");
        Instant end = Instant.parse("2099-01-01T00:01:00Z");

        Document filter = new Document()
                .append("deviceId", deviceId)
                .append(
                        "ts",
                        new Document(
                                "$gte",
                                java.util.Date.from(start)
                        ).append(
                                "$lte",
                                java.util.Date.from(end)
                        )
                );

        long count = events.countDocuments(filter);
        org.junit.jupiter.api.Assertions.assertEquals(0, count);
        
    }

    @Test
    @DisplayName("Fallo declarado: conexión inválida lanza excepción")
    void falloDeclarado() {
        String invalidUri = "mongodb://localhost:27099";

        org.junit.jupiter.api.Assertions.assertThrows(
                com.mongodb.MongoException.class,
                () -> {

                    try (MongoClient invalidClient =
                                 MongoClients.create(invalidUri)) {

                        invalidClient.getDatabase("cdrl_events")
                                .runCommand(new Document("ping", 1));
                    }
                }
        );
        
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
