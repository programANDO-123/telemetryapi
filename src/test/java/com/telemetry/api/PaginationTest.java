package com.telemetry.api;

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

import com.telemetry.api.service.AuditService;
import com.telemetry.api.service.EventService;

@DataMongoTest
@Import({ EventService.class, AuditService.class })
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class PaginationTest {

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
    @DisplayName("Caso normal: recorrer páginas secuenciales por cursor")
    void casoNormal() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 1: inserción a mitad de paginación no duplica ni salta")
    void estabilidadAnteInserciones() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 2: página fuera de rango devuelve vacío")
    void paginaFueraDeRango() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: offset se desfasa con inserciones concurrentes")
    void falloDeclaradoOffset() {
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
