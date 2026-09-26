package com.telemetry.api;

import java.time.Instant;

import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

class EventStoreTest {

    private static final String COLLECTION = "events";

    private static MongoClient client;
    private static MongoCollection<Document> events;

    @BeforeAll
    static void setUp() {
        String uri = requireEnv("MONGO_URI");
        String db  = requireEnv("MONGO_DB");

        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(db);
        events = database.getCollection(COLLECTION);

        events.createIndex(Indexes.ascending("ts"));
        events.createIndex(Indexes.ascending("deviceId"));
        events.createIndex(Indexes.ascending("type"));
    }

    @AfterAll
    static void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("Caso normal: insertar y consultar evento por rango")
    void casoNormal() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso límite 1: evento incompleto es rechazado")
    void casoLimite1() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso límite 2: rango vacío devuelve cero eventos")
    void casoLimite2() {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: conexión inválida lanza excepción")
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
