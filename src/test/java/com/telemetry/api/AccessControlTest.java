package com.telemetry.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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

        try (Connection migrationConnection = connectionForRole(
                "migration_role",
                requireEnv("MIGRATION_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = migrationConnection.createStatement()) {
                    statement.execute(
                            "CREATE TABLE IF NOT EXISTS public.m03_migration_probe " +
                            "(probe_id INTEGER PRIMARY KEY)"
                    );

                    statement.execute(
                            "DROP TABLE IF EXISTS public.m03_migration_probe"
                    );
                }
            });
        }

        try (Connection writerConnection = connectionForRole(
                "writer_role",
                requireEnv("WRITER_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = writerConnection.createStatement()) {
                    statement.executeUpdate(
                            "INSERT INTO telemetry_readings " +
                            "(device_id, recorded_at, speed_kmh, distance_km) " +
                            "VALUES (" +
                            "'11111111-1111-1111-1111-111111111111', " +
                            "TIMESTAMPTZ '2099-01-01 00:00:00+00', " +
                            "0.00, 0.000)" +
                            // Si intento insertar un registro y ya existe otro con el mismo device_id y recorded_at,
                            //  no hagas nada y continúa sin marcar error.
                            "ON CONFLICT (device_id, recorded_at) DO NOTHING"
                    );
                }
            });
        }

        try (Connection readerConnection = connectionForRole(
                "reader_role",
                requireEnv("READER_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = readerConnection.createStatement();
                     var resultSet = statement.executeQuery(
                             "SELECT vehicle_id FROM vehicles LIMIT 1")) {

                    assertEquals(true, resultSet.next());
                }
            });
        }

        try (Connection operatorConnection = connectionForRole(
                "operator_role",
                requireEnv("OPERATOR_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = operatorConnection.createStatement();
                     var resultSet = statement.executeQuery(
                             "SELECT pid FROM pg_stat_activity LIMIT 1")) {

                    assertEquals(true, resultSet.next());
                }
            });
        }
    }

    @Test
    @DisplayName("Caso límite 1: writer_role solo escribe en sus tablas")
    void casoLimite1() throws SQLException {

        try (Connection writerConnection = connectionForRole(
                "writer_role",
                requireEnv("WRITER_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = writerConnection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE telemetry_readings " +
                            "SET speed_kmh = speed_kmh " +
                            "WHERE reading_id = 1"
                    );
                }
            });

            assertDoesNotThrow(() -> {
                try (var statement = writerConnection.createStatement()) {
                    statement.executeUpdate(
                            "UPDATE alerts " +
                            "SET severity = severity " +
                            "WHERE alert_id = " +
                            "'99999999-9999-9999-9999-999999999999'"
                    );
                }
            });
        }
    }

    @Test
    @DisplayName("Caso límite 2: reader_role solo lee, no escribe")
    void casoLimite2() throws SQLException {

        try (Connection readerConnection = connectionForRole(
                "reader_role",
                requireEnv("READER_ROLE_PASSWORD"))) {

            assertDoesNotThrow(() -> {
                try (var statement = readerConnection.createStatement();
                     var resultSet = statement.executeQuery(
                             "SELECT vehicle_id, vin, make, model " +
                             "FROM vehicles LIMIT 1")) {

                    assertEquals(true, resultSet.next());
                }
            });
        }
    }

    @Test
    @DisplayName("Fallo declarado: PostgreSQL deniega la operación con SQLSTATE 42501")
    void falloDeclarado() throws SQLException {

        try (Connection operatorConnection = connectionForRole(
                "operator_role",
                requireEnv("OPERATOR_ROLE_PASSWORD"))) {

            try (var statement = operatorConnection.createStatement()) {

                statement.executeUpdate(
                        "INSERT INTO alerts " +
                        "(alert_id, reading_id, alert_type, severity, created_at) " +
                        "VALUES (" +
                        "'88888888-8888-8888-8888-888888888888', " +
                        "1, 'access_test', 'low', " +
                        "TIMESTAMPTZ '2099-01-01 00:00:00+00')"
                );

                fail("Se esperaba SQLSTATE 42501");

            } catch (SQLException exception) {
                assertEquals("42501", exception.getSQLState());
            }
        }
    }

    @Test
    @DisplayName("Negativa 1: writer_role no puede ejecutar DDL")
    void negativaWriterDDL() throws SQLException {

        try (Connection writerConnection = connectionForRole(
                "writer_role",
                requireEnv("WRITER_ROLE_PASSWORD"))) {

            try (var statement = writerConnection.createStatement()) {

                statement.execute(
                        "CREATE TABLE public.m03_writer_forbidden " +
                        "(id INTEGER)"
                );

                fail("Se esperaba SQLSTATE 42501");

            } catch (SQLException exception) {
                assertEquals("42501", exception.getSQLState());
            }
        }
    }

    @Test
    @DisplayName("Negativa 2: reader_role no puede insertar")
    void negativaReaderInsert() throws SQLException {

        try (Connection readerConnection = connectionForRole(
                "reader_role",
                requireEnv("READER_ROLE_PASSWORD"))) {

            try (var statement = readerConnection.createStatement()) {

                statement.executeUpdate(
                        "INSERT INTO alerts " +
                        "(alert_id, reading_id, alert_type, severity, created_at) " +
                        "VALUES (" +
                        "'77777777-7777-7777-7777-777777777777', " +
                        "1, 'access_test', 'low', " +
                        "TIMESTAMPTZ '2099-01-01 00:00:00+00')"
                );

                fail("Se esperaba SQLSTATE 42501");

            } catch (SQLException exception) {
                assertEquals("42501", exception.getSQLState());
            }
        }
    }

    @Test
    @DisplayName("Negativa 3: migration_role no puede acceder al catálogo de roles")
    void negativaMigrationSelect() throws SQLException {

        try(Connection migrationConnection = connectionForRole(
            "migration_role",
            requireEnv("MIGRATION_ROLE_PASSWORD"))) {

        try (var statement = migrationConnection.createStatement()) {

                statement.executeQuery(
                        "SELECT rolname FROM pg_authid LIMIT 1"
                );

                fail("Se esperaba SQLSTATE 42501");

            } catch (SQLException exception) {
                assertEquals("42501", exception.getSQLState());
            }
        }
    }

    private static Connection connectionForRole(
            String role,
            String password) throws SQLException {

        String host = requireEnv("POSTGRES_HOST");
        String port = requireEnv("POSTGRES_PORT");
        String db = requireEnv("POSTGRES_DB");

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;

        return DriverManager.getConnection(url, role, password);
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
