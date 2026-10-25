package com.telemetry.api;

import java.util.Map;

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

    @BeforeEach
    void clean() {
        service.deleteAll();
        auditService.deleteAll();
    }

    @Test
    @DisplayName("Caso normal: documento v1 se guarda y lee sin romperse")
    void casoNormalV1() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 1: coexistencia de v1 y v2 en la misma colección")
    void coexistenciaV1V2() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 2: actualización de v1 a v2 mantiene integridad")
    void actualizacionV1aV2() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: rollback sobre documento v1 no lo corrompe")
    void falloDeclaradoRollback() {
        // TODO: implementar
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
