package com.telemetry.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
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

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
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
        // Root autentica en admin, pero las pruebas operan en la base de la aplicación.
        registry.add("spring.mongodb.uri", () -> requireEnv("MONGO_ROOT_URI"));
        registry.add("spring.mongodb.database", () -> requireEnv("MONGO_DB"));
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    private final String eventId = "M09-" + UUID.randomUUID();

    @AfterEach
    void clean() {
        // Conservar documentos ajenos, validadores e índices de la colección.
        mongoTemplate.getCollection("document_events").deleteMany(eventFilter());
    }

    @Test
    @DisplayName("Caso normal: reader autorizado lee document_events")
    void casoNormal() {
        seedEvent();
        try (MongoClient client = connectAs(requireEnv("MONGO_READER_USER"),
                requireEnv("MONGO_READER_PASSWORD"))) {
            Document event = client.getDatabase(requireEnv("MONGO_DB"))
                    .getCollection("document_events").find(eventFilter()).first();
            assertNotNull(event);
            assertEquals(eventId, event.getString("eventId"));
        }
    }

    @Test
    @DisplayName("Límite 1: writer intenta leer audit_log y falla")
    void abusoLectura() {
        try (MongoClient client = connectAs(requireEnv("MONGO_WRITER_USER"),
                requireEnv("MONGO_WRITER_PASSWORD"))) {
            MongoDatabase database = client.getDatabase(requireEnv("MONGO_DB"));
            MongoCommandException denied = assertThrows(MongoCommandException.class,
                    () -> database.runCommand(new Document("find", "audit_log")
                            .append("filter", new Document()).append("limit", 1)));
            assertEquals(13, denied.getErrorCode(), "Debe fallar por Unauthorized");
        }
    }

    @Test
    @DisplayName("Límite 2: reader intenta escribir en document_events y falla")
    void abusoEscritura() {
        // Crear la colección con un documento válido antes de probar la autorización.
        seedEvent();
        try (MongoClient client = connectAs(requireEnv("MONGO_READER_USER"),
                requireEnv("MONGO_READER_PASSWORD"))) {
            MongoDatabase database = client.getDatabase(requireEnv("MONGO_DB"));
            Document attempted = eventDocument().append("_id", "denied-" + eventId);
            attempted.put("eventId", eventId + "-denied");
            MongoCommandException denied = assertThrows(MongoCommandException.class,
                    () -> database.runCommand(new Document("insert", "document_events")
                            .append("documents", List.of(attempted))));
            assertEquals(13, denied.getErrorCode(), "Debe fallar por Unauthorized");
            assertEquals(0L, mongoTemplate.getCollection("document_events")
                    .countDocuments(new Document("_id", "denied-" + eventId)));
            assertEquals(1L, mongoTemplate.getCollection("document_events").countDocuments(eventFilter()));
        } finally {
            mongoTemplate.getCollection("document_events")
                    .deleteMany(new Document("_id", "denied-" + eventId));
        }
    }

    @Test
    @DisplayName("Fallo declarado: remediación bloquea al usuario vulnerable")
    void remediacion() {
        String user = requireEnv("MONGO_VULN_USER");
        String password = requireEnv("MONGO_VULN_PASSWORD");
        MongoDatabase admin = mongoTemplate.getDb();
        Document broadRole = new Document("role", "readWrite").append("db", admin.getName());
        Document readerRole = new Document("role", "m09_reader_role").append("db", admin.getName());
        seedEvent();
        try {
            // Recrear el fallo solo para esta prueba, incluso si ya se ejecutó la remediación.
            admin.runCommand(new Document("grantRolesToUser", user).append("roles", List.of(broadRole)));
            try (MongoClient client = connectAs(user, password)) {
                assertEquals(1L, client.getDatabase(admin.getName()).getCollection("document_events")
                        .deleteOne(eventFilter()).getDeletedCount());
            }
            assertEquals(0L, mongoTemplate.getCollection("document_events").countDocuments(eventFilter()));
            seedEvent();
        } finally {
            // Mismas operaciones que db/mongo/remediation.js. Nunca dejar readWrite tras un fallo.
            admin.runCommand(new Document("revokeRolesFromUser", user).append("roles", List.of(broadRole)));
            admin.runCommand(new Document("grantRolesToUser", user).append("roles", List.of(readerRole)));
        }

        try (MongoClient client = connectAs(user, password)) {
            MongoDatabase database = client.getDatabase(admin.getName());
            MongoCommandException denied = assertThrows(MongoCommandException.class,
                    () -> database.runCommand(new Document("delete", "document_events")
                            .append("deletes", List.of(new Document("q", eventFilter()).append("limit", 1)))));
            assertEquals(13, denied.getErrorCode(), "La revocación debe impedir el borrado");
            assertNotNull(database.getCollection("document_events").find(eventFilter()).first(),
                    "El usuario remediado conserva la lectura");
            assertEquals(1L, mongoTemplate.getCollection("document_events").countDocuments(eventFilter()));
        }
        List<Document> users = admin.runCommand(new Document("usersInfo", user)).getList("users", Document.class);
        List<Document> roles = users.get(0).getList("roles", Document.class);
        assertTrue(roles.contains(readerRole));
        assertTrue(roles.stream().noneMatch(role -> "readWrite".equals(role.getString("role"))));
    }

    private Document eventFilter() {
        return new Document("eventId", eventId);
    }

    private Document eventDocument() {
        return new Document("eventId", eventId).append("type", "telemetry.security")
                .append("source", "synthetic-m09").append("timestamp", "2026-11-08T00:00:00Z")
                .append("payload", new Document("scenario", "security-controls"));
    }

    private void seedEvent() {
        mongoTemplate.getCollection("document_events").insertOne(eventDocument());
    }

    private static MongoClient connectAs(String user, String password) {
        String host = System.getenv().getOrDefault("MONGO_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("MONGO_PORT", "27017"));
        String db = requireEnv("MONGO_DB");
        // Credenciales separadas de la URI: admite caracteres especiales sin interpolar secretos.
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyToClusterSettings(builder -> builder.hosts(List.of(new ServerAddress(host, port))))
                .credential(MongoCredential.createCredential(user, db, password.toCharArray()))
                .build();
        return MongoClients.create(settings);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
