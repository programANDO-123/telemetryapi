// Configuración de roles y usuarios para M09.
const dbName = db.getName();

// 1. Rol de solo lectura para document_events
try {
  db.createRole({
    role: "m09_reader_role",
    privileges: [
      { resource: { db: dbName, collection: "document_events" }, actions: ["find"] }
    ],
    roles: []
  });
} catch (e) { if (e.code !== 51002) throw e; } // 51002 = Role already exists

// 2. Rol de lectura/escritura solo para document_events
try {
  db.createRole({
    role: "m09_writer_role",
    privileges: [
      { resource: { db: dbName, collection: "document_events" }, actions: ["find", "insert", "update"] }
    ],
    roles: []
  });
} catch (e) { if (e.code !== 51002) throw e; }

// 3. Crear usuarios
try {
  db.createUser({
    user: "m09_reader",
    pwd: "reader_pw_dev",
    roles: [{ role: "m09_reader_role", db: dbName }]
  });
} catch (e) { if (e.code !== 51003) throw e; } // 51003 = User already exists

try {
  db.createUser({
    user: "m09_writer",
    pwd: "writer_pw_dev",
    roles: [{ role: "m09_writer_role", db: dbName }]
  });
} catch (e) { if (e.code !== 51003) throw e; }

// 4. Usuario vulnerable (con permisos excesivos)
try {
  db.createUser({
    user: "m09_vuln",
    pwd: "vuln_pw_dev",
    roles: [{ role: "readWrite", db: dbName }]
  });
} catch (e) { if (e.code !== 51003) throw e; }

// 5. Usuario de aplicación para los tests normales (sin bypass de validación)
try {
  db.createUser({
    user: "test_app",
    pwd: "test_app_pw",
    roles: [{ role: "readWrite", db: dbName }]
  });
} catch (e) { if (e.code !== 51003) throw e; }

print("Security setup completed");
