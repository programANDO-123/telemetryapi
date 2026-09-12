-- Datos para el modelo operativo de M02.

INSERT INTO vehicles (vehicle_id, vin, make, model, year, device_id) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '3VWFE21C04M000001', 'Volkswagen', 'Jetta', 2020,
     '11111111-1111-1111-1111-111111111111'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '1HGCM82633A000002', 'Honda', 'Civic', 2021,
     '22222222-2222-2222-2222-222222222222'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', '1FTFW1ET5DFC00003', 'Ford', 'F-150', 2019,
     '33333333-3333-3333-3333-333333333333')
ON CONFLICT DO NOTHING;

INSERT INTO drivers (driver_id, license_number, curp, nombre, apellido_paterno, apellido_materno) VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'LIC-001-2020', 'PEGJ900101HDFRRN01', 'Juan', 'Perez', 'Garcia'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'LIC-002-2021', 'LOAM850505MDFPRR02', 'Maria', 'Lopez', 'Aranda'),
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'LIC-003-2019', 'RASF780808HDFXXX03', 'Sergio', 'Ramirez', 'Santos')
ON CONFLICT DO NOTHING;

INSERT INTO driver_vehicle_assignments (driver_id, vehicle_id, assigned_at) VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '2026-09-01 07:00:00+00'),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '2026-09-01 07:00:00+00'),
    ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '2026-09-01 07:00:00+00')
ON CONFLICT DO NOTHING;

INSERT INTO trips (trip_id, vehicle_id, driver_id, started_at, ended_at, status, idle_seconds) VALUES
    ('11111111-2222-3333-4444-555555555501', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'dddddddd-dddd-dddd-dddd-dddddddddddd', '2026-09-01 08:00:00+00', '2026-09-01 08:30:00+00',
     'completed', 120),
    ('11111111-2222-3333-4444-555555555502', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '2026-09-01 09:00:00+00', '2026-09-01 09:20:00+00',
     'completed', 60),
    ('11111111-2222-3333-4444-555555555503', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
     'ffffffff-ffff-ffff-ffff-ffffffffffff', '2026-09-01 10:00:00+00', NULL,
     'in_progress', 0)
ON CONFLICT DO NOTHING;
