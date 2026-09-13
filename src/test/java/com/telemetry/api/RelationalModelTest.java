package com.telemetry.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement; // Se agrego import
import java.sql.ResultSet; // Se agrego import
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals; // Se agrego import
import static org.junit.jupiter.api.Assertions.assertFalse; // Se agrego import
import static org.junit.jupiter.api.Assertions.assertThrows; // Se agrego import
import static org.junit.jupiter.api.Assertions.assertTrue; // Se agrego import

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
        // Consulta parametrizada para obtener un vehiculo existente.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vehicle_id, vin, make, model, year " +
                "FROM vehicles WHERE vin = ?")) {

            // AGREGADO: parametro de búsqueda.
            statement.setString(1, "3VWFE21C04M000001");

            // AGREGADO: ejecucion y validacion de los datos encontrados.
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("Volkswagen", result.getString("make"));
                assertEquals("Jetta", result.getString("model"));
                assertEquals(2020, result.getInt("year"));
            }
        }
    }

    @Test
    @DisplayName("Caso vacío: consulta parametrizada sin resultados")
    void casoVacio() throws SQLException {
        // Consulta parametrizada con un VIN inexistente.
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT vehicle_id FROM vehicles WHERE vin = ?")) {

            // AGREGADO: parametro que no existe en la base.
            statement.setString(1, "VIN-QUE-NO-EXISTE");

            // AGREGADO: validacion de que no existen resultados.
            try (ResultSet result = statement.executeQuery()) {
                assertFalse(result.next());
            }
        }
    }

    @Test
    @DisplayName("Caso límite: valor en el borde de una restricción")
    void casoLimite() throws SQLException {
        // Insercion parametrizada usando el limite valido year = 1900.
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vehicles " +
                "(vehicle_id, vin, make, model, year) " +
                "VALUES (?, ?, ?, ?, ?)")) {

            // AGREGADO: valores utilizados para probar el limite de la restriccion.
            statement.setObject(1, java.util.UUID.randomUUID());
            // Esto genera un VIN (Numero de identificacion del vehiculo)
            // diferente en cada ejecucion, pero siempre de exactamente 17 caracteres
            statement.setString(2, "LIMIT1900" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            statement.setString(3, "Test");
            statement.setString(4, "Limit");
            statement.setInt(5, 1900);

            // AGREGADO: validacion de que la insercion fue realizada.
            assertEquals(1, statement.executeUpdate());
        }
    }

    @Test
    @DisplayName("Fallo declarado: la base rechaza un dato inválido")
    void falloDeclarado() throws SQLException {
        // Insercion parametrizada con un año invalido.
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO vehicles " +
                "(vehicle_id, vin, make, model, year) " +
                "VALUES (?, ?, ?, ?, ?)")) {

            // AGREGADO: valores invalidos para provocar el CHECK de year >= 1900.
            statement.setObject(1, java.util.UUID.randomUUID());
            statement.setString(2, "INVALID-1900-VIN");
            statement.setString(3, "Test");
            statement.setString(4, "Invalid");
            statement.setInt(5, 1899);

            // AGREGADO: se espera que PostgreSQL rechace el dato.
            assertThrows(SQLException.class, statement::executeUpdate);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + name);
        }
        return value;
    }
}
