// Bootstrap local para volúmenes anteriores a M09. Ejecutar dentro del contenedor,
// por loopback. Con autenticación activa, MongoDB solo permite crear el primer
// usuario sin credenciales cuando todavía no existen usuarios ni roles.
const adminDb = db.getSiblingDB("admin");
const username = process.env.MONGO_INITDB_ROOT_USERNAME;
const password = process.env.MONGO_INITDB_ROOT_PASSWORD;

if (!username || !password) {
  print("Missing MONGO_INITDB_ROOT_USERNAME or MONGO_INITDB_ROOT_PASSWORD in the mongo container.");
  quit(1);
}

let authenticated = false;
try {
  authenticated = Boolean(adminDb.auth(username, password));
} catch (error) {
  // Solo un fallo de autenticación permite intentar el bootstrap inicial.
  if (error.code !== 18) {
    print("Cannot verify the MongoDB administrator. Check that the server is ready.");
    quit(1);
  }
}

if (!authenticated) {
  try {
    adminDb.createUser({
      user: username,
      pwd: password,
      roles: [{ role: "root", db: "admin" }]
    });
    print("Created the first MongoDB administrator using the localhost exception.");
  } catch (error) {
    print("Cannot initialize the MongoDB administrator. Existing users or roles may prevent the localhost exception.");
    print("Configure .env with the existing administrator credentials, or recover access with an authorized administrator.");
    print("The existing data volume has been preserved.");
    quit(1);
  }

  try {
    authenticated = Boolean(adminDb.auth(username, password));
  } catch (error) {
    print("MongoDB administrator authentication failed after initialization.");
    quit(1);
  }
}

if (!authenticated) {
  print("MongoDB administrator authentication failed.");
  quit(1);
}

print("MongoDB administrative authentication verified.");
