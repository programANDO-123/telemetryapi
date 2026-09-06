-- checar el contrato relacional para la telemetria
-- de velocidad y distancia.

CREATE TABLE IF NOT EXISTS devices (
    device_id UUID PRIMARY KEY,
    device_code VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS telemetry_readings (
    reading_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id UUID NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    speed_kmh NUMERIC(8, 2) NOT NULL CHECK (speed_kmh >= 0),
    distance_km NUMERIC(10, 3) NOT NULL CHECK (distance_km >= 0),
    CONSTRAINT telemetry_readings_device_recorded_at_key
        UNIQUE (device_id, recorded_at),
    CONSTRAINT telemetry_readings_device_id_fkey
        FOREIGN KEY (device_id) REFERENCES devices (device_id)
);
