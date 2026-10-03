package com.telemetry.api;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

class DocumentStoreTest {

    private static final String COLLECTION = "document_events";

    private static MongoClient client;
    private static MongoCollection<Document> events;

    @BeforeAll
    static void setUp() {
        String uri = requireEnv("MONGO_URI");
        String db  = requireEnv("MONGO_DB");

        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(db);
        events = database.getCollection(COLLECTION);
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Happy path: insertar y consultar evento valido por eventId")
    void happyPath() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Duplicado: mismo eventId no se inserta dos veces")
    void duplicado() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Ausencia: consultar eventId inexistente devuelve cero")
    void ausencia() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: documento sin campo requerido es rechazado")
    void falloDeclarado() {
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
