// Remediación de permisos excesivos
// Revoca el acceso de escritura al usuario vulnerable

const dbName = db.getName();

//Revocar el rol readWrite
db.revokeRolesFromUser(
  "m09_vuln",
  [{ role: "readWrite", db: dbName }]
);

//Asignar un rol restringido
db.grantRolesToUser(
  "m09_vuln",
  [{ role: "m09_reader_role", db: dbName }]
);

print("Remediation applied to m09_vuln");
