package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.telemetry.api.model.Event;
import com.telemetry.api.service.EventService;

import jakarta.validation.ConstraintViolationException;

@DataMongoTest
@Import(EventService.class)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class DocumentStoreTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> requireEnv("MONGO_URI"));
        registry.add("spring.data.mongodb.database", () -> requireEnv("MONGO_DB"));
    }

    @Autowired
    private EventService service;

    @BeforeEach
    void clean() {
        service.deleteAll();
    }

    @Test
    @DisplayName("Caso normal: crear y leer evento por eventId")
    void casoNormal() {
        Event event = event("M05-HAPPY-001", "telemetry.created", "GPS-001", "2026-10-03T20:00:00Z");

        service.save(event);

        Optional<Event> found = service.findById("M05-HAPPY-001");

        assertTrue(found.isPresent());
        assertEquals("telemetry.created", found.get().getType());
        assertEquals("GPS-001", found.get().getSource());
    }

    @Test
    @DisplayName("Consulta por type y rango de timestamp usa índice declarado")
    void consultaPorTypeYTimestamp() {
        service.save(event("M05-RANGE-001", "telemetry.temp", "GPS-001", "2026-10-03T20:00:00Z"));
        service.save(event("M05-RANGE-002", "telemetry.temp", "GPS-002", "2026-10-03T20:05:00Z"));
        service.save(event("M05-RANGE-003", "telemetry.speed", "GPS-003", "2026-10-03T20:10:00Z"));

        List<Event> hits = service.findByTypeInRange(
                "telemetry.temp",
                "2026-10-03T19:00:00Z",
                "2026-10-03T21:00:00Z");

        assertEquals(2, hits.size());
    }

    @Test
    @DisplayName("Duplicado: mismo eventId no se inserta dos veces")
    void duplicado() {
        Event event = event("M05-DUP-001", "telemetry.created", "GPS-001", "2026-10-03T20:00:00Z");

        service.save(event);
        service.save(event);

        assertEquals(1, service.count());
    }

    @Test
    @DisplayName("Ausencia: consultar eventId inexistente devuelve vacío")
    void ausencia() {
        Optional<Event> found = service.findById("M05-NO-EXISTE");

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Actualización idempotente: guardar dos veces con mismo eventId no duplica")
    void updateIdempotente() {
        Event event = event("M05-UPD-001", "telemetry.created", "GPS-001", "2026-10-03T20:00:00Z");
        service.save(event);

        event.setType("telemetry.updated");
        service.save(event);
        service.save(event);

        assertEquals(1, service.count());
        assertEquals("telemetry.updated", service.findById("M05-UPD-001").get().getType());
    }

    @Test
    @DisplayName("Eliminación idempotente: eliminar dos veces no falla")
    void deleteIdempotente() {
        service.save(event("M05-DEL-001", "telemetry.created", "GPS-001", "2026-10-03T20:00:00Z"));

        assertTrue(service.deleteById("M05-DEL-001"));
        assertTrue(!service.deleteById("M05-DEL-001"));
        assertEquals(0, service.count());
    }

    @Test
    @DisplayName("Fallo declarado: documento sin campo requerido es rechazado")
    void falloDeclarado() {
        Event invalid = new Event();
        invalid.setEventId("M05-INVALID-001");
        invalid.setSource("GPS-001");
        // falta type, timestamp, payload

        assertThrows(ConstraintViolationException.class, () -> service.save(invalid));
        assertEquals(0, service.count());
    }

    private static Event event(String eventId, String type, String source, String timestamp) {
        return new Event(
                eventId,
                type,
                source,
                timestamp,
                Map.of("speedKmh", 80, "distanceKm", 120.5),
                Map.of("schemaVersion", 1, "receivedBy", "test"));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
