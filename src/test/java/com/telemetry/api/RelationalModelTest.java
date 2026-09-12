package com.telemetry.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RelationalModelTest {

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
    @DisplayName("Caso normal: consulta parametrizada devuelve datos")
    void casoNormal() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso vacío: consulta parametrizada sin resultados")
    void casoVacio() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Caso límite: valor en el borde de una restricción")
    void casoLimite() throws SQLException {
        // TODO: implementar
    }

    @Test
    @DisplayName("Fallo declarado: la base rechaza un dato inválido")
    void falloDeclarado() throws SQLException {
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
