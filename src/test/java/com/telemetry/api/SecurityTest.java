package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.telemetry.api.service.AuditService;
import com.telemetry.api.service.EventService;

@DataMongoTest
@Import({ EventService.class, AuditService.class })
@ImportAutoConfiguration(ValidationAutoConfiguration.class)
class SecurityTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        // SecurityTest usa ROOT para poder manipular roles y colecciones
        registry.add("spring.mongodb.uri", () -> requireEnv("MONGO_ROOT_URI"));
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection("document_events");
        mongoTemplate.dropCollection("audit_log");
    }

    @Test
    @DisplayName("Caso normal: reader autorizado lee document_events")
    void casoNormal() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 1: writer intenta leer audit_log y falla")
    void abusoLectura() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Límite 2: reader intenta escribir en document_events y falla")
    void abusoEscritura() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: remediación bloquea al usuario vulnerable")
    void remediacion() {
        // TODO: implementar
    }

    private static MongoClient connectAs(String user, String password) {
        String host = System.getenv().getOrDefault("MONGO_HOST", "localhost");
        String port = System.getenv().getOrDefault("MONGO_PORT", "27017");
        String db = requireEnv("MONGO_DB");
        String authenticatedUri = "mongodb://" + user + ":" + password + "@" + host + ":" + port + "/" + db
                + "?authSource=" + db;
        return MongoClients.create(authenticatedUri);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
