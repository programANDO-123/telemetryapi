
package com.telemetry.api;

import java.util.Map;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

import com.telemetry.api.model.Event;
import com.telemetry.api.service.AuditService;
import com.telemetry.api.service.EventService;

@DataMongoTest
@Import({ EventService.class, AuditService.class })
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class VersioningTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> requireEnv("MONGO_URI"));
        registry.add("spring.mongodb.database", () -> requireEnv("MONGO_DB"));
    }

    @Autowired
    private EventService service;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        service.deleteAll();
        auditService.deleteAll();
    }

    @Test
    @DisplayName("Caso normal: documento v1 se guarda y lee sin romperse")
    void casoNormalV1() {
        Event event = createEvent("version-test-normal");
        Event saved = service.save(event);

        assertEquals(1, saved.getSchemaVersion());

        Event read = service.findByEventId("version-test-normal")
                .orElseThrow();

        assertEquals("version-test-normal", read.getEventId());
        assertEquals("telemetry", read.getType());
        assertEquals(1, read.getSchemaVersion());
        assertEquals(42.5, read.getPayload().get("speed_kmh"));
    }

    @Test
    @DisplayName("Límite 1: coexistencia de v1 y v2 en la misma colección")
    void coexistenciaV1V2() {
        Event v1 = createEvent("version-test-v1");
        service.save(v1);

        Event v2 = createEvent("version-test-v2");
        v2.setSchemaVersion(2);
        v2.setFirmwareVersion("1.2.0");
        v2.setQualityScore(0.98);
        service.save(v2);

        Event readV1 = service.findByEventId("version-test-v1")
                .orElseThrow();
        Event readV2 = service.findByEventId("version-test-v2")
                .orElseThrow();

        assertEquals(1, readV1.getSchemaVersion());
        assertNull(readV1.getFirmwareVersion());
        assertNull(readV1.getQualityScore());

        assertEquals(2, readV2.getSchemaVersion());
        assertEquals("1.2.0", readV2.getFirmwareVersion());
        assertEquals(0.98, readV2.getQualityScore());

        assertEquals(2, service.count());
    }

    @Test
    @DisplayName("Límite 2: actualización de v1 a v2 mantiene integridad")
    void actualizacionV1aV2() {
        Event original = createEvent("version-test-update");
        service.save(original);

        Event updated = createEvent("version-test-update");
        updated.setSchemaVersion(2);
        updated.setFirmwareVersion("2.0.0");
        updated.setQualityScore(0.95);

        Event saved = service.save(updated);

        Event read = service.findByEventId("version-test-update")
                .orElseThrow();

        assertEquals(2, read.getSchemaVersion());
        assertEquals("2.0.0", read.getFirmwareVersion());
        assertEquals(0.95, read.getQualityScore());
        assertEquals(42.5, read.getPayload().get("speed_kmh"));

        assertEquals(original.getId(), saved.getId());
        assertEquals(1, service.count());
    }

    @Test
    @DisplayName("Fallo declarado: rollback no modifica documentos v1")
    void falloDeclaradoRollback() {
        Event v1 = createEvent("version-test-rollback-v1");
        service.save(v1);

        Document before = mongoTemplate.getCollection("document_events")
                .find(new Document("eventId", "version-test-rollback-v1"))
                .first();

        assertNotNull(before);
        assertEquals(1, before.getInteger("schemaVersion"));

        // Reproduce el filtro y los campos del rollback v2 -> v1.
        // Solo se seleccionan documentos con schemaVersion = 2.
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("schemaVersion").is(2)),
                new Update().unset("schemaVersion")
                        .unset("firmwareVersion")
                        .unset("qualityScore"),
                "document_events");

        Document after = mongoTemplate.getCollection("document_events")
                .find(new Document("eventId", "version-test-rollback-v1"))
                .first();

        assertNotNull(after);
        assertEquals(before.getString("eventId"), after.getString("eventId"));
        assertEquals(before.getString("type"), after.getString("type"));
        assertEquals(before.get("payload"), after.get("payload"));
        assertEquals(1, after.getInteger("schemaVersion"));
    }

    private Event createEvent(String eventId) {
        return new Event(
                eventId,
                "telemetry",
                "vehicle-01",
                "2026-10-25T10:00:00Z",
                Map.of("speed_kmh", 42.5, "distance_km", 120.0),
                Map.of("source", "synthetic-test"));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing required env var: " + name);
        }
        return value;
    }
}
