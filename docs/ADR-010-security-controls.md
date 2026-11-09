# ADR-010 — Controles de seguridad por colección y operación

## Estado

Aceptado por el equipo para M09.

## Contexto

Telemetry API almacena eventos en `document_events` y trazas de aplicación en
`audit_log`. Una cuenta con `readWrite` sobre toda la base puede modificar o borrar
eventos y también alterar la evidencia de auditoría. El usuario sintético
`m09_vuln` reproduce ese exceso de privilegios en el entorno local.

Las identidades funcionales app_reader y app_writer corresponden, en
`db/mongo/security_setup.js`, a los usuarios MongoDB `m09_reader` y `m09_writer`.
Los nombres de sus roles personalizados son `m09_reader_role` y
`m09_writer_role`. Los controles deben aplicarse en MongoDB aunque un cliente
omita la API y se conecte directamente.

## Decisión

Aplicar mínimo privilegio por colección y operación en la base `MONGO_DB`:

| Identidad | Rol | document_events | audit_log |
| --- | --- | --- | --- |
| app_reader / m09_reader | m09_reader_role | find | Sin permisos |
| app_writer / m09_writer | m09_writer_role | find, insert, update | Sin permisos |
| m09_vuln antes de remediar | readWrite | Lectura, escritura y borrado | Lectura y escritura |
| m09_vuln después de remediar | m09_reader_role | find | Sin permisos |

Los roles personalizados declaran recursos de colección concretos y no heredan
otros roles. El lector necesita consultar eventos; el escritor necesita consultar,
insertar y actualizar. Ninguno necesita `remove`, administrar usuarios, cambiar
el esquema ni consultar o modificar auditoría. Separar estas capacidades reduce
el daño posible ante abuso de una cuenta. No se otorga `readWrite` general para
resolver una operación puntual.

La aplicación registra mediante `AuditService` los campos `actorId`, `action`,
`entity`, `entityId`, `timestamp`, `traceId`, `result` y `reason`. Se usan motivos
controlados como `validation_error` y `not_found`, sin copiar payloads, contraseñas,
tokens, URIs autenticadas ni excepciones completas. Los identificadores y motivos
deben permanecer libres de secretos. La auditoría permite correlacionar acciones
sin conservar material de autenticación.

`AuditService` no implementa redacción automática de entradas arbitrarias. La
prueba `AuditTest.sinSecretos` verifica un registro sintético seguro; no demuestra
que cualquier cadena proporcionada por un llamador será sanitizada. Las llamadas
directas del driver en `SecurityTest` tampoco generan entradas en `audit_log`:
el rechazo se comprueba mediante el código MongoDB 13 (`Unauthorized`).

La identidad administrativa se inicializa mediante `db/mongo/bootstrap_admin.js`
cuando el volumen local todavía no contiene usuarios ni roles. El script usa la
conexión de loopback para crear el primer administrador y verifica después su
autenticación. Si ya existen usuarios y las credenciales configuradas no permiten
acceso, se detiene y conserva el volumen. Este paso prepara la administración del
laboratorio; la revocación del usuario vulnerable se realiza por separado.

Plan de remediación:

1. Identificar la cuenta, sus roles efectivos y las colecciones afectadas con una
   identidad administrativa, conservando evidencia sin credenciales.
2. Revocar a `m09_vuln` el rol `readWrite` de `MONGO_DB`.
3. Conceder únicamente `m09_reader_role`, según `db/mongo/remediation.js`.
4. Abrir una conexión nueva con la misma identidad: el borrado debe devolver
   `Unauthorized`, el documento debe sobrevivir y la lectura debe seguir operativa.
5. Comprobar con `usersInfo` que `readWrite` desapareció y el rol lector está presente.
   Si hubiera otros roles amplios, revisarlos y revocarlos antes de cerrar el incidente.
6. Mantener el estado restringido; si hubiera exposición real de credenciales,
   rotarlas y revisar el alcance del incidente. La prueba solo usa datos sintéticos.

`SecurityTest.remediacion` recrea temporalmente el rol excesivo para demostrar el
antes y el después de forma repetible. Lo revoca en un bloque `finally`, incluso
si falla la comprobación previa. Esa concesión temporal solo pertenece al laboratorio.

## Validación de la decisión

La ejecución local de `make verify` del 8 de noviembre de 2026 terminó con
`M09 verification passed` y Maven informó `BUILD SUCCESS`: 49 pruebas ejecutadas,
sin fallos, errores ni omisiones. Se verificó la creación inicial del administrador
y su autenticación, manteniendo el volumen existente.

Los cuatro casos de `SecurityTest` pasaron: el lector recuperó un evento, el
escritor no pudo consultar `audit_log`, el lector no pudo insertar eventos y
`m09_vuln` perdió la capacidad de borrar después de revocar `readWrite`. Las
operaciones denegadas devolvieron el código 13; el caso de remediación también
comprobó que el evento permanecía, la lectura seguía permitida y `usersInfo` ya
no incluía el rol excesivo.

Las cuatro pruebas de `AuditTest`, incluida `sinSecretos`, pasaron y el escaneo
informó `No versioned secrets detected`. Estas comprobaciones respaldan los casos
implementados y los datos sintéticos utilizados; se mantienen los límites de
sanitización y cobertura de auditoría descritos en esta decisión.

Los resultados se sustentan en `artifacts/m09-verify.json` y los informes de
`SecurityTest` y `AuditTest` en `target/surefire-reports/`. El detalle de la
verificación se documenta en [Reporte M09](M09-report.md).

## Alternativas consideradas

- `readWrite` para ambas cuentas: sencillo, pero permite borrados y acceso a
  colecciones ajenas a su función, incluida la auditoría. Rechazado.
- Solo validar permisos en la API: una conexión directa a MongoDB elude ese control.
  Rechazado como protección única.
- Deshabilitar por completo al usuario vulnerable: apropiado si la cuenta ya no
  necesita acceso; para M09 se conserva lectura de eventos y se elimina escritura.
- Conceder al escritor acceso a `audit_log`: amplía sus capacidades y permite
  manipular evidencia. La integración de auditoría requiere una identidad separada
  con privilegios mínimos, fuera de los dos roles de eventos actuales.

## Consecuencias

- Las operaciones no autorizadas fallan en el servidor, independientemente del cliente.
- Se deben mantener roles y pruebas cuando cambien las necesidades funcionales.
- Root se reserva para configuración y pruebas administrativas. La URI puede
  autenticar en `admin`, pero las pruebas seleccionan explícitamente `MONGO_DB`.
- La cuenta `test_app` del laboratorio sigue teniendo `readWrite` para las pruebas
  generales y `AuditService`; no equivale a una identidad restringida de producción.
- La auditoría actual no es inmutable y no cubre por sí sola todo acceso directo a
  MongoDB. Quedan como mejoras una identidad exclusiva de auditoría y controles de
  integridad, retención y sanitización de entradas.
- Las pruebas de seguridad usan documentos sintéticos propios y conservan los
  índices, validadores y documentos ajenos durante su limpieza.
