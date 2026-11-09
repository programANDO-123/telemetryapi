# Reporte M09 — Controles, abuso y remediación

## Objetivo

Verificar el mínimo privilegio en MongoDB y demostrar que revocar un rol excesivo
impide nuevos borrados de eventos, conservando la lectura autorizada.

## Alcance

Base seleccionada por `MONGO_DB`, colecciones `document_events` y `audit_log`,
roles definidos en `db/mongo/security_setup.js` y remediación definida en
`db/mongo/remediation.js`. app_reader y app_writer son los nombres funcionales de
los usuarios `m09_reader` y `m09_writer`; no son cuentas adicionales.

Las conexiones de cada actor se crean con `connectAs(user, password)`. Usuarios y
contraseñas se obtienen de las variables `MONGO_READER_*`, `MONGO_WRITER_*` y
`MONGO_VULN_*`. La conexión administrativa usa `MONGO_ROOT_URI` y selecciona
explícitamente `MONGO_DB`, aunque root autentique en `admin`. Las credenciales no
se incluyen en este reporte.

## Controles por colección y operación

| Usuario | Rol | Acceso a document_events | Acceso a audit_log |
| --- | --- | --- | --- |
| m09_reader | m09_reader_role | find | Ninguno |
| m09_writer | m09_writer_role | find, insert, update | Ninguno |
| m09_vuln, estado vulnerable | readWrite de MONGO_DB | Lectura, escritura y borrado | Lectura y escritura |
| m09_vuln, estado remediado | m09_reader_role | find | Ninguno |

Los roles personalizados no conceden `remove` ni administración. Se verifica el
código 13 (`Unauthorized`) para distinguir una denegación real de problemas de
conectividad, autenticación o validación de documentos. La justificación completa
se encuentra en [ADR-010](ADR-010-security-controls.md).

## Auditoría (sin secretos)

`AuditService.record` persiste actor, acción, entidad, identificador, instante,
traceId, resultado y motivo. `EventService` registra guardados y borrados, con
motivos controlados para fallos de validación o eventos inexistentes. No se deben
registrar contraseñas, tokens, payloads completos, URIs autenticadas ni mensajes
de excepción que puedan contenerlos.

`AuditTest.sinSecretos` comprueba la ausencia de marcadores de secretos en un
registro sintético. Es una comprobación limitada: el servicio no sanitiza
cadenas arbitrarias y no representa auditoría nativa del servidor. Las denegaciones
de las conexiones directas usadas en `SecurityTest` no se escriben automáticamente
en `audit_log`. Sus evidencias son las aserciones de autorización de las pruebas.

## Prueba de abuso

Escenario: `m09_vuln` recibió `readWrite` sobre toda la base, por lo que puede
borrar eventos sin necesitar permiso funcional de eliminación. También tiene
acceso a auditoría, lo que amplía el impacto potencial.

`SecurityTest.remediacion` reproduce el escenario con un evento sintético cuyo
identificador es único por prueba:

1. Inserta el evento con la conexión administrativa y concede temporalmente
   `readWrite` a `m09_vuln`, para repetir el escenario aunque ya se hubiera remediado.
2. Conecta como `m09_vuln` y ejecuta `deleteOne` únicamente sobre ese evento.
3. Exige un documento borrado y confirma administrativamente que ya no existe.
4. Vuelve a insertar el evento para comprobar su protección tras la revocación.

La demostración no borra datos de negocio. No se debe recrear el rol vulnerable
fuera del entorno de pruebas.

## Remediación

La prueba ejecuta las mismas operaciones administrativas que el script existente:
`revokeRolesFromUser` elimina `readWrite` de `m09_vuln` en `MONGO_DB` y
`grantRolesToUser` añade `m09_reader_role`. Se realizan en `finally` para retirar
el permiso excesivo aunque falle una aserción durante la demostración inicial.

Después abre una conexión nueva con las mismas credenciales y exige que:

- El comando de borrado lance `MongoCommandException` con código 13.
- El evento protegido siga existiendo, comprobado con la conexión administrativa.
- La cuenta remediada todavía pueda leer ese evento.
- `usersInfo` incluya el rol lector y ya no incluya `readWrite`.

La remediación cambia la autorización de la cuenta. No restaura documentos
previamente borrados; el evento de esta prueba se repone como fixture sintético.

## Pruebas

| Método de SecurityTest | Comprobación verificada | Resultado |
| --- | --- | --- |
| casoNormal | m09_reader lee y recupera el evento esperado de document_events. | Aprobado |
| abusoLectura | m09_writer recibe MongoCommandException / 13 al consultar audit_log. | Aprobado |
| abusoEscritura | m09_reader recibe MongoCommandException / 13 al insertar; no aparece el documento intentado y el original permanece. | Aprobado |
| remediacion | El borrado pasa antes de revocar readWrite; después falla con código 13, el evento permanece y la lectura sigue permitida. | Aprobado |

Todos los clientes de los actores se cierran con try-with-resources. La limpieza
elimina únicamente los documentos sintéticos propios, conservando datos ajenos,
índices y validadores. Se usan documentos válidos para no confundir un error de
esquema con una denegación de permisos.

Si el volumen local fue creado antes de configurar autenticación, ejecutar primero
`make verify`: espera al proceso MongoDB definitivo y ejecuta
`db/mongo/bootstrap_admin.js`. Este comprueba la autenticación administrativa y,
si no hay usuarios ni roles, crea el primer administrador usando la excepción de
localhost. Conserva el volumen; si ya existen usuarios y las credenciales no son
válidas, se detiene para solicitar la configuración administrativa correcta. El
healthcheck autentica leyendo el entorno desde mongosh, sin interpolar una URI.

Para ejecutar solo las pruebas, una vez inicializado el administrador, desde Git
Bash, con `.env` configurado y Docker accesible:

```bash
set -a
source .env
set +a
docker compose up -d mongo
# Esperar a que MongoDB esté healthy antes de aplicar los scripts.
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/schema.js
docker compose exec -T mongo mongosh -u "$MONGO_ROOT_USER" -p "$MONGO_ROOT_PASSWORD" --authenticationDatabase admin --quiet "$MONGO_DB" < db/mongo/security_setup.js
mvn -B -Dtest=SecurityTest,AuditTest test
```

La verificación completa del proyecto se ejecuta con `make verify`.

## Evidencia

La ejecución de `make verify` realizada por el usuario desde WSL el 8 de noviembre
de 2026 finalizó con `M09 verification passed`. Se contrastó la salida compartida
con el artefacto generado y los informes locales de Surefire. Maven terminó a las
22:31:48 (UTC-06:00) con `BUILD SUCCESS`; después se completaron el restore y el
benchmark.

| Comprobación | Resultado observado |
| --- | --- |
| Suite completa | 49 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| SecurityTest | 4 pruebas aprobadas; incluye el abuso y la revocación de permisos. |
| AuditTest | 4 pruebas aprobadas; incluye sinSecretos. |
| Administración MongoDB | Primer administrador creado y autenticación verificada. |
| Esquema y seguridad | Aplicados antes de ejecutar las pruebas. |
| Migración M07 | Ciclo v1 -> v2 -> v1 -> v2 completado. |
| Escaneo de secretos | No versioned secrets detected. |
| Restore | ok. |
| Benchmark M08 | ok. |
| Verificación M09 | passed. |

Fuentes de evidencia del proyecto:

- [artifacts/m09-verify.json](../artifacts/m09-verify.json): registra `status=passed`,
  el resumen de 49 pruebas, `restore=ok` y `benchmarkStatus=ok`.
- [Informe SecurityTest](../target/surefire-reports/com.telemetry.api.SecurityTest.txt):
  confirma las cuatro pruebas sin fallos, errores ni omisiones.
- [Informe AuditTest](../target/surefire-reports/com.telemetry.api.AuditTest.txt):
  confirma las cuatro pruebas de auditoría sin fallos, errores ni omisiones.
- La salida compartida de `make verify` confirma la inicialización administrativa,
  el escaneo de secretos, las migraciones y el cierre exitoso del script.

Los informes de `target/` son salidas locales regenerables y están ignorados por
Git. El archivo `evidence/m09-security-remediation.json` conserva su plantilla
manual con valores `pending`; no se utiliza como evidencia de esta ejecución.

La excepción de conexión a `localhost:27099` que aparece en la salida corresponde
a `EventStoreTest.falloDeclarado`: ese caso intenta conectarse deliberadamente a
un puerto inválido y exige una `MongoException`. La clase terminó con sus cuatro
pruebas aprobadas, por lo que esa excepción es parte del resultado esperado.

## Límites conocidos

- La verificación corresponde a un entorno local de laboratorio con datos
  sintéticos; no acredita una configuración de producción ni acceso a otros entornos.
- La prueba modifica los roles de `m09_vuln`: debe ejecutarse en una base de
  laboratorio aislada y sin otra ejecución concurrente que use esa cuenta.
- La cuenta administrativa puede alterar usuarios y datos; no debe ser la
  identidad de ejecución de la aplicación en producción.
- Los scripts de configuración conservan roles y usuarios existentes; si se
  modificaron manualmente, hay que revisar sus permisos efectivos.
- La cuenta general `test_app` conserva `readWrite`, y `audit_log` no dispone de
  garantías de inmutabilidad. Los dos roles restringidos de eventos no pueden
  ejecutar por sí solos operaciones de EventService que también escriben auditoría.
- El escaneo de secretos y la prueba con datos seguros no garantizan sanitización
  universal ni cobertura de todos los accesos directos al servidor.

## Conclusión

Se documentaron los controles y se validaron los cuatro casos con conexiones por
usuario. `m09_vuln` pudo borrar con el rol excesivo y recibió `Unauthorized` al
intentar borrar después de revocar `readWrite`; el documento protegido permaneció
y la lectura siguió permitida. La comprobación administrativa confirmó el rol
lector y la ausencia de `readWrite`.

La verificación M09 quedó aprobada: 49 pruebas sin fallos, errores ni omisiones,
restore y benchmark correctos, y cierre de `make verify` con
`M09 verification passed`. Se mantienen las limitaciones de auditoría y del
entorno de laboratorio indicadas en este reporte.
