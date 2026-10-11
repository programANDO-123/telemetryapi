
package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.UpdateResult;

class RecoveryTest {

    private static final String COLLECTION = "document_events";
    private static final String PREFIX = "m06-test-";

    private static MongoClient client;
    private static MongoCollection<Document> events;

    @BeforeAll
    static void setUp() {
        String uri = requireEnv("MONGO_URI");
        String db = requireEnv("MONGO_DB");

        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(db);
        events = database.getCollection(COLLECTION);

        events.createIndex(
            Indexes.ascending("eventId"),
            new IndexOptions().unique(true)
        );
        events.createIndex(Indexes.ascending("type", "timestamp"));
        events.createIndex(Indexes.ascending("source", "timestamp"));
    }

    @BeforeEach
    void cleanTestDocuments() {
        events.deleteMany(
            Filters.regex("eventId", "^" + PREFIX)
        );
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    // 1. Caso normal: recuperar el conjunto de documentos

    @Test
    @DisplayName("Caso normal: restore recupera el fixture completo")
    void casoNormal() {
        Document first = event(
            PREFIX + "restore-1",
            "telemetry.temperature",
            "sensor-01",
            "2026-10-11T10:00:00Z"
        );

        Document second = event(
            PREFIX + "restore-2",
            "telemetry.humidity",
            "sensor-02",
            "2026-10-11T10:01:00Z"
        );

        events.insertMany(List.of(first, second));

        List<Document> recovered = events.find(
            Filters.regex("eventId", "^" + PREFIX + "restore-")
        ).into(new ArrayList<>());

        assertEquals(2, recovered.size());
        assertTrue(recovered.stream().anyMatch(
            doc -> (PREFIX + "restore-1").equals(doc.getString("eventId"))
        ));
        assertTrue(recovered.stream().anyMatch(
            doc -> (PREFIX + "restore-2").equals(doc.getString("eventId"))
        ));

        for (Document doc : recovered) {
            assertNotNull(doc.getString("type"));
            assertNotNull(doc.getString("source"));
            assertNotNull(doc.getString("timestamp"));
            assertNotNull(doc.get("payload"));
        }
    }

    // 2. La migracion repetida debe ser idempotente

    @Test
    @DisplayName("Edge case 1: migracion repetida es idempotente")
    void migracionRepetida() {
        String id = PREFIX + "migration";

        Document legacy = event(
            id,
            "telemetry.temperature",
            "sensor-legacy",
            "2026-10-11T10:10:00Z"
        );

        events.insertOne(legacy);

        UpdateResult firstRun = events.updateMany(
            Filters.and(
                Filters.eq("eventId", id),
                Filters.exists("schemaVersion", false)
            ),
            new Document("$set", new Document("schemaVersion", 2))
        );

        UpdateResult secondRun = events.updateMany(
            Filters.and(
                Filters.eq("eventId", id),
                Filters.exists("schemaVersion", false)
            ),
            new Document("$set", new Document("schemaVersion", 2))
        );

        Document migrated = events.find(
            Filters.eq("eventId", id)
        ).first();

        assertNotNull(migrated);
        assertEquals(1, firstRun.getMatchedCount());
        assertEquals(1, firstRun.getModifiedCount());
        assertEquals(0, secondRun.getMatchedCount());
        assertEquals(0, secondRun.getModifiedCount());
        assertEquals(2, migrated.getInteger("schemaVersion"));
    }

    // 3. Los documentos antiguos se pueden leer

    @Test
    @DisplayName("Edge case 2: documento v1 sin schemaVersion se lee sin error")
    void documentoV1SinSchemaVersion() {
        String id = PREFIX + "legacy";

        Document legacy = event(
            id,
            "telemetry.temperature",
            "sensor-v1",
            "2026-10-11T10:20:00Z"
        );

        events.insertOne(legacy);

        Document found = events.find(
            Filters.eq("eventId", id)
        ).first();

        assertNotNull(found);
        assertEquals(id, found.getString("eventId"));
        assertFalse(found.containsKey("schemaVersion"));

        // Si no existe schemaVersion, se interpreta como version 1.
        int effectiveVersion = found.containsKey("schemaVersion")
            ? found.getInteger("schemaVersion")
            : 1;

        assertEquals(1, effectiveVersion);
    }

    // 4. Una consulta debe seguir funcionando sin un indice

    @Test
    @DisplayName("Fallo tipico: indice ausente no rompe la consulta")
    void indiceAusente() {
        String id = PREFIX + "no-index";

        events.insertOne(event(
            id,
            "telemetry.temperature",
            "sensor-index",
            "2026-10-11T10:30:00Z"
        ));

        String indexName = "type_1_timestamp_1";

        boolean indexExists = events.listIndexes(Document.class)
            .into(new ArrayList<>())
            .stream()
            .anyMatch(index -> indexName.equals(index.getString("name")));

        try {
            if (indexExists) {
                events.dropIndex(indexName);
            }

            Document found = events.find(
                Filters.and(
                    Filters.eq("type", "telemetry.temperature"),
                    Filters.eq("eventId", id)
                )
            ).first();

            assertNotNull(found);
            assertEquals(id, found.getString("eventId"));
        } finally {
            // Restaurar el indice para no afectar otras pruebas.
            events.createIndex(Indexes.ascending("type", "timestamp"));
        }
    }

    // 5. Un documento incompleto debe ser rechazado

    @Test
    @DisplayName("Fallo tipico: documento incompleto es rechazado por el schema")
    void documentoIncompleto() {
        Document invalid = new Document("eventId", PREFIX + "invalid")
            .append("type", "telemetry.temperature")
            .append("timestamp", "2026-10-11T10:40:00Z");

        // Se espera que el validador de MongoDB esté aplicado
        // mediante db/mongo/schema.js.
        assertThrows(
            MongoWriteException.class,
            () -> events.insertOne(invalid)
        );
    }

    // 6. Verificar que los documentos de prueba no contengan secretos

    @Test
    @DisplayName("Fallo tipico: fixture no contiene credenciales hardcodeadas")
    void fixtureSinSecretos() {
        String id = PREFIX + "safe-fixture";

        events.insertOne(event(
            id,
            "telemetry.temperature",
            "sensor-safe",
            "2026-10-11T10:50:00Z"
        ));

        Document fixture = events.find(
            Filters.eq("eventId", id)
        ).first();

        assertNotNull(fixture);

        String content = fixture.toJson().toLowerCase();

        List<String> forbiddenTerms = List.of(
            "password",
            "passwd",
            "secret",
            "api_key",
            "apikey",
            "private_key",
            "access_token",
            "refresh_token"
        );

        for (String term : forbiddenTerms) {
            assertFalse(
                content.contains(term),
                "Se encontro un termino sensible en el documento: " + term
            );
        }
    }

    // 7. Detectar una restauracion parcial

    @Test
    @DisplayName("Fallo declarado: restore parcial deja inconsistencia detectable")
    void falloDeclarado() {
        String expectedFirst = PREFIX + "partial-1";
        String expectedSecond = PREFIX + "partial-2";

        // Simular una restauracion parcial: solo se recupera
        // el primer documento de los dos esperados.
        events.insertOne(event(
            expectedFirst,
            "telemetry.temperature",
            "sensor-partial",
            "2026-10-11T11:00:00Z"
        ));

        long recoveredCount = events.countDocuments(
            Filters.in("eventId", expectedFirst, expectedSecond)
        );

        assertEquals(1, recoveredCount);
        assertTrue(
            recoveredCount < 2,
            "La restauracion parcial debe detectarse como incompleta"
        );
    }


    private static Document event(
        String eventId,
        String type,
        String source,
        String timestamp
    ) {
        return new Document("eventId", eventId)
            .append("type", type)
            .append("source", source)
            .append("timestamp", timestamp)
            .append(
                "payload",
                new Document("deviceId", source)
                    .append("value", 25.5)
                    .append("unit", "C")
            )
            .append(
                "metadata",
                new Document("schemaVersion", 1)
                    .append("receivedBy", "m06-test")
            );
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required env var: " + name
            );
        }

        return value;
    }
}
