# Reporte M07 — Versionado y migración compatible

## Objetivo

Implementar y verificar la evolución de los documentos de eventos de v1 a v2, manteniendo la compatibilidad con documentos existentes y permitiendo regresar al formato v1 mediante un rollback controlado.

## Alcance

El hito M07 comprende:

- Compatibilidad de documentos v1 y v2 en la colección `document_events` de MongoDB.
- Normalización de la versión en `EventService`.
- Incorporación de los campos `firmwareVersion` y `qualityScore` para v2.
- Migración mediante `db/mongo/migrate_v1_to_v2.js`.
- Rollback mediante `db/mongo/rollback_v2_to_v1.js`.
- Pruebas de lectura, coexistencia, actualización y protección de documentos v1 frente al rollback.
- Verificación del ciclo v1 → v2 → v1 → v2 mediante `make verify`.
- Ejecución de las pruebas existentes y del restore de fixture heredado de M06.

## Estrategia de versionado (v1 / v2)

Se utiliza el campo `schemaVersion` para identificar la versión del documento.

Los documentos sin `schemaVersion` se interpretan como v1. Al guardar un evento sin versión, `EventService` asigna el valor `1`. La consulta mediante `findByEventId` también normaliza a v1 los eventos cuya versión es nula.

Para los eventos v1, el servicio establece `firmwareVersion` y `qualityScore` en `null`, evitando conservar valores exclusivos de v2.

Los eventos v2 utilizan `schemaVersion: 2` e incorporan:

- `firmwareVersion`: versión del firmware.
- `qualityScore`: indicador de calidad.

Al guardar un evento v2, si estos valores son nulos, el servicio asigna `"unknown"` y `0.0`, respectivamente.

Ambas versiones pueden coexistir en la misma colección. La actualización de un evento con el mismo `eventId` reutiliza su identificador interno, evitando crear un documento adicional.

## Migración y rollback

La migración v1 → v2 se ejecuta mediante `db/mongo/migrate_v1_to_v2.js`.

El script de verificación realiza el siguiente procedimiento:

1. Inserta el documento sintético v1 `M07-V1-001`.
2. Ejecuta la migración a v2.
3. Comprueba que existe exactamente un documento con ese `eventId` y `schemaVersion: 2`.
4. Ejecuta el rollback a v1.
5. Comprueba que el documento existe y ya no tiene `schemaVersion`.
6. Vuelve a ejecutar la migración y verifica nuevamente su versión v2.
7. Elimina el documento sintético.

El rollback selecciona únicamente documentos con `schemaVersion: 2` y elimina, mediante `$unset`, los campos:

- `schemaVersion`
- `firmwareVersion`
- `qualityScore`

Esto devuelve los documentos al formato v1 implícito. Los documentos con versión explícita `1` o sin versión quedan fuera del filtro.

Por su diseño, repetir el rollback sin cambios intermedios no vuelve a seleccionar los documentos ya revertidos. Sin embargo, elimina los valores de los campos exclusivos de v2; recuperarlos requiere un respaldo.

## Pruebas

La ejecución proporcionada de `make verify` terminó con `BUILD SUCCESS` y el siguiente resultado global:

`Tests run: 41, Failures: 0, Errors: 0, Skipped: 0`

Dentro de este total, `VersioningTest` ejecutó cuatro pruebas sin fallos:

| Caso | Comprobación |
| --- | --- |
| Documento v1 | Guarda y consulta un evento, comprobando versión, identificador, tipo y un valor del payload. |
| Coexistencia v1 / v2 | Mantiene ambas versiones en la colección y comprueba sus campos específicos y el total de documentos. |
| Actualización v1 → v2 | Conserva el identificador interno y un valor del payload, actualiza los campos v2 y mantiene un solo documento. |
| Rollback sobre v1 | Reproduce la operación de rollback y comprueba que un documento con `schemaVersion: 1` conserva su identificador de evento, tipo, payload y versión. |

La verificación también completó el ciclo de migración, rollback y nueva migración. El análisis de secretos indicó `No versioned secrets detected`.

El artefacto proporcionado registra el restore de fixture con resultado `ok`.

## Evidencia

Los archivos asociados al hito son:

- `src/test/java/com/telemetry/api/VersioningTest.java`
- `src/main/java/com/telemetry/api/service/EventService.java`
- `db/mongo/migrate_v1_to_v2.js`
- `db/mongo/rollback_v2_to_v1.js`
- `scripts/verify_base.sh`
- `artifacts/m07-verify.json`
- `evidence/m07-document-versioning.json`

El contenido proporcionado de `artifacts/m07-verify.json` registra:

{
  "command": "make verify",
  "status": "passed",
  "tests": "Tests run: 41, Failures: 0, Errors: 0, Skipped: 0",
  "restore": "ok"
}

La salida de consola muestra:

- Primera migración: 2 documentos coincidentes y 2 modificados.
- Rollback: 5 documentos coincidentes y 5 modificados.
- Nueva migración: 5 documentos coincidentes y 5 modificados.
- Limpieza del documento sintético: 1 documento eliminado.

Estos conteos corresponden a las operaciones sobre la colección, no exclusivamente al documento sintético.

El archivo `evidence/m07-document-versioning.json` proporcionado todavía contiene resultados pendientes y `PENDING_TAG`; debe actualizarse para cerrar la trazabilidad del hito.

## Límites conocidos

- El rollback elimina los valores de `firmwareVersion` y `qualityScore`; volver a migrar no garantiza recuperar sus valores anteriores.
- El rollback afecta a todos los documentos con `schemaVersion: 2`, no solamente al documento sintético.
- El ciclo v1 → v2 → v1 → v2 demuestra que es posible volver a migrar después del rollback. No demuestra por sí solo la idempotencia de dos migraciones consecutivas sobre documentos ya migrados.
- No se muestra una ejecución consecutiva del rollback dos veces; su idempotencia se fundamenta en el filtro utilizado.
- La prueba Java de rollback reproduce la operación con `MongoTemplate`; el script JavaScript se ejecuta en la verificación de consola.
- La normalización de lectura mostrada se aplica a `findByEventId`; no se acredita el mismo tratamiento en todas las consultas.
- La evidencia presentada no demuestra rechazo de versiones distintas de 1 y 2 ni comportamiento bajo escrituras concurrentes o grandes volúmenes.

## Conclusión

M07 incorpora compatibilidad entre documentos v1 y v2, actualización sin duplicación del evento probado y un procedimiento de retorno al formato v1.

La ejecución proporcionada reporta 41 pruebas aprobadas, incluyendo cuatro de versionado, y el artefacto registra un restore exitoso. El ciclo de migración y rollback se completó correctamente en el entorno verificado.

Queda pendiente actualizar el archivo de evidencia con los resultados y la referencia definitiva del commit o tag.
