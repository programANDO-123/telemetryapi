package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.telemetry.api.model.AuditEntry;
import com.telemetry.api.service.AuditService;

@DataMongoTest
@Import(AuditService.class)
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class AuditTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> requireEnv("MONGO_URI"));
        registry.add("spring.mongodb.database", () -> requireEnv("MONGO_DB"));
    }

    @Autowired
    private AuditService service;

    @BeforeEach
    void clean() {
        service.deleteAll();
    }

    @Test
    @DisplayName("Caso normal: registrar operación y consultarla por traceId")
    void casoNormal() {
        AuditEntry saved = service.record(
            "user-17",
            "UPDATE",
            "vehicle",
            "vehicle-001",
            "trace-001",
            "SUCCESS",
            "Actualizacion de datos");

        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("user-17", saved.getActorId());
        assertEquals("UPDATE", saved.getAction());
        assertEquals("vehicle", saved.getEntity());
        assertEquals("vehicle-001", saved.getEntityId());
        assertEquals("trace-001", saved.getTraceId());
        assertEquals("SUCCESS", saved.getResult());
        assertEquals("Actualizacion de datos", saved.getReason());
        assertNotNull(saved.getTimestamp());

        List<AuditEntry> entries = service.findByTraceId("trace-001");

        assertEquals(1, entries.size());
        assertEquals("trace-001", entries.get(0).getTraceId());
    }

    @Test
    @DisplayName("Edge case 1: multiple operaciones con mismo traceId se agrupan")
    void multiplesConMismoTrace() {
        String traceId = "trace-multiple-001";

        service.record("user-17", "CREATE", "vehicle",
                "vehicle-001", traceId, "SUCCESS", "Creacion");

        service.record("user-17", "UPDATE", "vehicle",
                "vehicle-001", traceId, "SUCCESS", "Actualizacion");

        service.record("user-18", "READ", "vehicle",
                "vehicle-001", traceId, "SUCCESS", "Consulta");

        List<AuditEntry> entries = service.findByTraceId(traceId);

        assertEquals(3, entries.size());

        assertTrue(entries.stream()
                .allMatch(entry -> traceId.equals(entry.getTraceId())));
    }

    @Test
    @DisplayName("Edge case 2: consulta por entity devuelve solo esa entidad")
    void consultaPorEntity() {
        service.record("user-17", "CREATE", "vehicle",
                "vehicle-001", "trace-vehicle-001",
                "SUCCESS", "Alta de vehiculo");

        service.record("user-17", "UPDATE", "vehicle",
                "vehicle-002", "trace-vehicle-002",
                "SUCCESS", "Actualizacion de vehiculo");

        service.record("user-17", "CREATE", "driver",
                "driver-001", "trace-driver-001",
                "SUCCESS", "Alta de conductor");

        List<AuditEntry> vehicles = service.findByEntity("vehicle");

        assertEquals(2, vehicles.size());

        assertTrue(vehicles.stream()
                .allMatch(entry -> "vehicle".equals(entry.getEntity())));

        List<AuditEntry> drivers = service.findByEntity("driver");

        assertEquals(1, drivers.size());
        assertEquals("driver", drivers.get(0).getEntity());
    }

    @Test
    @DisplayName("Fallo declarado: audit no contiene secretos")
    void sinSecretos() {
        service.record(
                "user-safe",
                "UPDATE",
                "vehicle",
                "vehicle-safe-001",
                "trace-safe-001",
                "SUCCESS",
                "Actualizacion rutinaria");

        List<AuditEntry> entries =
                service.findByTraceId("trace-safe-001");

        assertEquals(1, entries.size());

        AuditEntry entry = entries.get(0);

        String auditText = String.join(" ",
                entry.getActorId(),
                entry.getAction(),
                entry.getEntity(),
                entry.getEntityId(),
                entry.getTimestamp(),
                entry.getTraceId(),
                entry.getResult(),
                entry.getReason())
                .toLowerCase(java.util.Locale.ROOT);

        assertFalse(auditText.contains("password"));
        assertFalse(auditText.contains("token"));
        assertFalse(auditText.contains("secret"));
        assertFalse(auditText.contains("api_key"));
        assertFalse(auditText.contains("private_key"));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
