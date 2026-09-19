-- M03: roles separados para migracion, escritura, lectura y operacion
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'migration_role') THEN
        CREATE ROLE migration_role LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'writer_role') THEN
        CREATE ROLE writer_role LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'reader_role') THEN
        CREATE ROLE reader_role LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'operator_role') THEN
        CREATE ROLE operator_role LOGIN;
    END IF;
END
$$;

ALTER ROLE migration_role WITH PASSWORD :'migration_pw';
ALTER ROLE writer_role    WITH PASSWORD :'writer_pw';
ALTER ROLE reader_role    WITH PASSWORD :'reader_pw';
ALTER ROLE operator_role  WITH PASSWORD :'operator_pw';

GRANT USAGE, CREATE ON SCHEMA public TO migration_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO migration_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO migration_role;

GRANT USAGE ON SCHEMA public TO writer_role;
GRANT SELECT, INSERT, UPDATE ON telemetry_readings TO writer_role;
GRANT SELECT, INSERT, UPDATE ON alerts TO writer_role;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO writer_role;

GRANT USAGE ON SCHEMA public TO reader_role;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO reader_role;

GRANT pg_monitor TO operator_role;
