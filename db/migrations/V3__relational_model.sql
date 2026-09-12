-- Modelo operativo  M02.
-- Idempotente: se puede reejecutar sin error.

CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id UUID PRIMARY KEY,
    vin VARCHAR(17) NOT NULL UNIQUE,
    make VARCHAR(64) NOT NULL,
    model VARCHAR(64) NOT NULL,
    year INT NOT NULL CHECK (year >= 1900),
    device_id UUID UNIQUE REFERENCES devices (device_id)
);

CREATE TABLE IF NOT EXISTS drivers (
    driver_id UUID PRIMARY KEY,
    license_number VARCHAR(32) NOT NULL UNIQUE,
    curp VARCHAR(18) NOT NULL UNIQUE,
    nombre VARCHAR(64) NOT NULL,
    apellido_paterno VARCHAR(64) NOT NULL,
    apellido_materno VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS driver_vehicle_assignments (
    driver_id UUID NOT NULL REFERENCES drivers (driver_id),
    vehicle_id UUID NOT NULL REFERENCES vehicles (vehicle_id),
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (driver_id, vehicle_id, assigned_at)
);

CREATE TABLE IF NOT EXISTS trips (
    trip_id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicles (vehicle_id),
    driver_id UUID NOT NULL REFERENCES drivers (driver_id),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    status VARCHAR(16) NOT NULL CHECK (status IN ('in_progress', 'completed', 'cancelled')),
    idle_seconds INT NOT NULL DEFAULT 0 CHECK (idle_seconds >= 0),
    CONSTRAINT trips_time_check CHECK (ended_at IS NULL OR ended_at > started_at)
);

ALTER TABLE telemetry_readings
    ADD COLUMN IF NOT EXISTS trip_id UUID REFERENCES trips (trip_id);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id UUID PRIMARY KEY,
    reading_id BIGINT NOT NULL REFERENCES telemetry_readings (reading_id),
    alert_type VARCHAR(32) NOT NULL,
    severity VARCHAR(8) NOT NULL CHECK (severity IN ('low', 'medium', 'high')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE OR REPLACE VIEW driving_patterns AS
SELECT
    t.driver_id,
    count(*) AS total_trips,
    avg(r.speed_kmh) AS avg_speed,
    sum(r.distance_km) AS total_distance
FROM trips t
JOIN telemetry_readings r ON r.trip_id = t.trip_id
WHERE t.status = 'completed'
GROUP BY t.driver_id;
