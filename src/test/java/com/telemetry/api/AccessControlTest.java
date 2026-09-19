package com.telemetry.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlTest {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws SQLException {
        String host = requireEnv("POSTGRES_HOST");
        String port = requireEnv("POSTGRES_PORT");
        String db = requireEnv("POSTGRES_DB");
        String user = requireEnv("POSTGRES_USER");
        String password = requireEnv("POSTGRES_PASSWORD");

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        connection = DriverManager.getConnection(url, user, password);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Caso normal: cada rol ejecuta la operación permitida")
    void casoNormal() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso límite 1: writer_role solo escribe en sus tablas")
    void casoLimite1() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso límite 2: reader_role solo lee, no escribe")
    void casoLimite2() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: PostgreSQL deniega la operación con SQLSTATE 42501")
    void falloDeclarado() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Negativa 1: writer_role no puede ejecutar DDL")
    void negativaWriterDDL() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Negativa 2: reader_role no puede insertar")
    void negativaReaderInsert() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Negativa 3: migration_role no puede leer datos de negocio")
    void negativaMigrationSelect() throws SQLException {
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
