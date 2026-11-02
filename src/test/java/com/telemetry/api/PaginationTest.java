
package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
    @DisplayName("Caso normal: recorrer paginas secuenciales por cursor")
    void casoNormal() {
        List<String> expected = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Event saved = service.save(createEvent(
                    "normal-" + i, timestamp(i)));
            expected.add(saved.getEventId());
        }

        List<String> actual = new ArrayList<>();
        String cursor = null;

        while (true) {
            List<Event> page = service.findPageByCursor(cursor, 2);

            if (page.isEmpty()) {
                break;
            }

            for (Event event : page) {
                actual.add(event.getEventId());
            }

            cursor = page.get(page.size() - 1).getTimestamp();
        }

        assertEquals(expected, actual,
                "La paginacion por cursor debe recuperar todos los eventos en orden");
        assertEquals(actual.size(), actual.stream().distinct().count(),
                "No debe haber eventos duplicados");
    }

    @Test
    @DisplayName("Limite 1: inserción a mitad de paginacion no duplica ni salta")
    void estabilidadAnteInserciones() {
        List<String> originalIds = new ArrayList<>();

        for (int i = 1; i <= 4; i++) {
            Event saved = service.save(createEvent(
                    "stable-" + i, timestamp(i)));
            originalIds.add(saved.getEventId());
        }

        List<Event> firstPage = service.findPageByCursor(null, 2);
        assertEquals(2, firstPage.size());

        String cursor = firstPage.get(firstPage.size() - 1).getTimestamp();

        // Insercion posterior al cursor actual.
        service.save(createEvent("stable-inserted", timestamp(5)));

        List<String> collected = new ArrayList<>();
        firstPage.forEach(event -> collected.add(event.getEventId()));

        while (true) {
            List<Event> page = service.findPageByCursor(cursor, 2);

            if (page.isEmpty()) {
                break;
            }

            page.forEach(event -> collected.add(event.getEventId()));
            cursor = page.get(page.size() - 1).getTimestamp();
        }

        for (String originalId : originalIds) {
            assertTrue(collected.contains(originalId),
                    "No debe omitirse el evento original: " + originalId);
        }

        assertEquals(collected.size(),
                collected.stream().distinct().count(),
                "La paginacion por cursor no debe duplicar eventos");

        assertTrue(collected.contains("stable-inserted"),
                "La insercion posterior al cursor debe poder recorrerse");
    }

    @Test
    @DisplayName("Limite 2: pagina fuera de rango devuelve vacio")
    void paginaFueraDeRango() {
        for (int i = 1; i <= 3; i++) {
            service.save(createEvent("range-" + i, timestamp(i)));
        }

        List<Event> page = service.findPageByOffset(10, 2);

        assertTrue(page.isEmpty(),
                "Una pagina fuera de rango debe devolver una lista vacia");

        List<Event> cursorPage =
                service.findPageByCursor("2099-01-01T00:00:00Z", 2);

        assertTrue(cursorPage.isEmpty(),
                "Un cursor posterior a todos los eventos debe devolver vacio");
    }

    @Test
    @DisplayName("Fallo declarado: offset se desfasa con inserciones concurrentes")
    void falloDeclaradoOffset() {
        for (int i = 1; i <= 4; i++) {
            service.save(createEvent("offset-" + i, timestamp(i)));
        }

        List<Event> firstPage = service.findPageByOffset(0, 2);
        assertEquals(2, firstPage.size());

        String lastFirstPageId =
                firstPage.get(firstPage.size() - 1).getEventId();

        // Inserción anterior a los registros existentes: desplaza los índices.
        service.save(createEvent(
                "offset-inserted", "2025-12-31T23:59:59Z"));

        List<Event> secondPage = service.findPageByOffset(1, 2);
        assertEquals(2, secondPage.size());

        List<String> firstIds = firstPage.stream()
                .map(Event::getEventId)
                .toList();

        List<String> secondIds = secondPage.stream()
                .map(Event::getEventId)
                .toList();

        assertTrue(firstIds.contains(lastFirstPageId));
        assertTrue(secondIds.contains(lastFirstPageId),
                "La insercion anterior debe provocar un duplicado entre paginas");

        assertFalse(secondIds.contains("offset-4"),
                "El registro final se desplaza fuera de las dos paginas consultadas");
    }

    private Event createEvent(String eventId, String timestamp) {
        return new Event(
                eventId,
                "pagination_test",
                "test-source",
                timestamp,
                Map.of("value", 1),
                Map.of());
    }

    private String timestamp(int second) {
        return "2026-01-01T00:00:%02dZ".formatted(second);
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
