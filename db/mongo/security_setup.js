// configuracion de roles y usuarios para M09
// Usa roles personalizados para limitar el acceso por coleccion, evita readWrite global

const dbName = db.getName();

// Rol de solo lectura
if (!db.getRole("m09_reader_role")) {
  db.createRole({
    role: "m09_reader_role",
    privileges: [
      { resource: { db: dbName, collection: "document_events" }, actions: ["find"] }
    ],
    roles: []
  });
}

//Rol de lectura/escritura
if (!db.getRole("m09_writer_role")) {
  db.createRole({
    role: "m09_writer_role",
    privileges: [
      { resource: { db: dbName, collection: "document_events" }, actions: ["find", "insert", "update"] }
    ],
    roles: []
  });
}

//Crear usuarios idemotente
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

// 4. Usuario vulnerable con permisos muchos
try {
  db.createUser({
    user: "m09_vuln",
    pwd: "vuln_pw_dev",
    roles: [{ role: "readWrite", db: dbName }]
  });
} catch (e) { if (e.code !== 51003) throw e; }

print("Security setup completed");
