
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TelemetryContractTest {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws SQLException {

        String db = System.getenv("POSTGRES_DB");
        String user = System.getenv("POSTGRES_USER");
        String password = System.getenv("POSTGRES_PASSWORD");
        String port = System.getenv("POSTGRES_PORT");

        if (db == null || user == null || password == null || port == null) {
            throw new IllegalStateException(
                    "Faltan las variables de entorno POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD o POSTGRES_PORT.");
        }

        String url = "jdbc:postgresql://localhost:" + port + "/" + db;

        connection = DriverManager.getConnection(
                url,
                user,
                password);

        assertTrue(
                deviceExists("GPS-001"),
                "El seed debe contener el dispositivo GPS-001");
    }

    @AfterAll
    static void tearDown() throws SQLException {

        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Caso normal: inserta una lectura válida")
    void casoNormal() throws SQLException {

        long readingId = insertReading(
                45.50,
                7.250);

        try {

            assertTrue(
                    readingId > 0,
                    "La lectura debe generar un reading_id");

            assertReadingValues(
                    readingId,
                    45.50,
                    7.250);

        } finally {

            deleteReading(readingId);
        }
    }

    @Test
    @DisplayName("Caso límite 1: velocidad mínima igual a cero")
    void limiteVelocidadCero() throws SQLException {

        long readingId = insertReading(
                0.00,
                1.000);

        try {

            assertTrue(
                    readingId > 0,
                    "La lectura con velocidad cero debe ser válida");

            assertReadingValues(
                    readingId,
                    0.00,
                    1.000);

        } finally {

            deleteReading(readingId);
        }
    }

    @Test
    @DisplayName("Caso límite 2: distancia mínima igual a cero")
    void limiteDistanciaCero() throws SQLException {

        long readingId = insertReading(
                1.00,
                0.000);

        try {

            assertTrue(
                    readingId > 0,
                    "La lectura con distancia cero debe ser válida");

            assertReadingValues(
                    readingId,
                    1.00,
                    0.000);

        } finally {

            deleteReading(readingId);
        }
    }

    @Test
    @DisplayName("Fallo declarado: velocidad negativa debe ser rechazada")
    void falloVelocidadNegativa() throws SQLException {

        OffsetDateTime recordedAt = nextTimestamp();
        UUID deviceId = gps001Id();

        SQLException error = assertThrows(
                SQLException.class,
                () -> {

                    String sql = """
                            INSERT INTO telemetry_readings
                                (device_id, recorded_at, speed_kmh, distance_km)
                            VALUES (?, ?, ?, ?)
                            """;

                    try (PreparedStatement statement = connection.prepareStatement(sql)) {

                        statement.setObject(
                                1,
                                deviceId);

                        statement.setObject(
                                2,
                                recordedAt);

                        statement.setBigDecimal(
                                3,
                                new BigDecimal("-0.01"));

                        statement.setBigDecimal(
                                4,
                                new BigDecimal("1.000"));

                        statement.executeUpdate();
                    }
                });

        assertEquals(
                "23514",
                error.getSQLState(),
                "La base debe rechazar el valor negativo mediante una restricción CHECK");
    }

    private static long insertReading(
            double speed,
            double distance) throws SQLException {

        String sql = """
                INSERT INTO telemetry_readings
                    (device_id, recorded_at, speed_kmh, distance_km)
                VALUES (?, ?, ?, ?)
                RETURNING reading_id
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(
                    1,
                    gps001Id());

            statement.setObject(
                    2,
                    nextTimestamp());

            statement.setBigDecimal(
                    3,
                    new BigDecimal(
                            String.valueOf(speed)));

            statement.setBigDecimal(
                    4,
                    new BigDecimal(
                            String.valueOf(distance)));

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(
                        result.next(),
                        "La inserción debe devolver un reading_id");

                return result.getLong(
                        "reading_id");
            }
        }
    }

    private static void assertReadingValues(
            long readingId,
            double expectedSpeed,
            double expectedDistance) throws SQLException {

        String sql = """
                SELECT speed_kmh, distance_km
                FROM telemetry_readings
                WHERE reading_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    readingId);

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(
                        result.next(),
                        "La lectura insertada debe existir");

                BigDecimal actualSpeed = result.getBigDecimal("speed_kmh");

                BigDecimal actualDistance = result.getBigDecimal("distance_km");

                BigDecimal expectedSpeedValue = new BigDecimal(
                        String.valueOf(expectedSpeed));

                BigDecimal expectedDistanceValue = new BigDecimal(
                        String.valueOf(expectedDistance));

                assertEquals(
                        0,
                        actualSpeed.compareTo(
                                expectedSpeedValue),
                        "La velocidad no coincide");

                assertEquals(
                        0,
                        actualDistance.compareTo(
                                expectedDistanceValue),
                        "La distancia no coincide");
            }
        }
    }

    private static void deleteReading(
            long readingId) throws SQLException {

        String sql = """
                DELETE FROM telemetry_readings
                WHERE reading_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    readingId);

            statement.executeUpdate();
        }
    }

    private static boolean deviceExists(
            String code) throws SQLException {

        String sql = """
                SELECT 1
                FROM devices
                WHERE device_code = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(
                    1,
                    code);

            try (ResultSet result = statement.executeQuery()) {

                return result.next();
            }
        }
    }

    private static UUID gps001Id()
            throws SQLException {

        String sql = """
                SELECT device_id
                FROM devices
                WHERE device_code = 'GPS-001'
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(
                        result.next(),
                        "No se encontró GPS-001 en el seed");

                return result.getObject(
                        "device_id",
                        UUID.class);
            }
        }
    }

    private static OffsetDateTime nextTimestamp()
            throws SQLException {

        String sql = """
                SELECT COALESCE(
                    MAX(recorded_at) + INTERVAL '1 second',
                    CURRENT_TIMESTAMP
                )
                FROM telemetry_readings
                WHERE device_id = ?
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(
                    1,
                    gps001Id());

            try (ResultSet result = statement.executeQuery()) {

                assertTrue(
                        result.next());

                return result.getObject(
                        1,
                        OffsetDateTime.class).withOffsetSameInstant(
                                ZoneOffset.UTC);
            }
        }
    }
}
