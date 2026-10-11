# Reporte M06 — Evolución, auditoría y recuperación

## Objetivo

Documentar la evolución compatible de eventos v1 a v2, la trazabilidad operativa sin secretos y la recuperación de un fixture sintético. Evaluar la integridad de los datos recuperados y definir las métricas RPO, RTO y SLO con el alcance real del ejercicio.

## Alcance

M06 comprende la colección MongoDB `document_events`, su validación e índices, la migración mediante `schemaVersion`, los registros de `AuditService`, la integración mostrada con `EventService` y el flujo de recuperación de tres documentos sintéticos.

Este reporte se basa en los fragmentos de código y la salida de `make verify` proporcionados. Los fragmentos contienen marcas de diff y líneas duplicadas; se describen sus comportamientos y se usa el log como evidencia de la ejecución, sin asumir que el texto pegado es una copia compilable del código final.

## Evolución documental (v1 / v2)

Los eventos v1 sin `schemaVersion` en la raíz siguen siendo consultables y se interpretan como versión 1. Los eventos v2 incluyen `schemaVersion: 2` en la raíz.

La migración selecciona documentos sin el campo raíz y agrega el valor `2` mediante `$set`. La prueba `migracionRepetida` espera un documento modificado en la primera ejecución y ninguno en la segunda, demostrando idempotencia para ese caso. La prueba `documentoV1SinSchemaVersion` consulta un documento heredado y aplica la interpretación de versión 1 dentro de la prueba.

El fixture de pruebas incluye `metadata.schemaVersion: 1`; ese valor anidado se distingue del discriminador raíz. La actualización mostrada no elimina ni sincroniza la versión anidada.

La salida de `make verify` informa que se aplicaron el esquema y los índices `eventId_1`, `type_1_timestamp_-1` y `source_1_timestamp_-1`. La migración de esta ejecución reportó `Matched: 1` y `Modified: 1`: encontró y actualizó un documento elegible. La idempotencia se verifica adicionalmente mediante la prueba `migracionRepetida`.

## Auditoría operativa

Cada entrada contiene actor, acción, entidad, identificador de entidad, fecha/hora, `traceId`, resultado y motivo. Las consultas permiten recuperar operaciones con el mismo `traceId` y filtrar por entidad.

Los fragmentos de `EventService` registran guardados exitosos, rechazos por validación, eliminaciones exitosas e intentos de eliminar eventos inexistentes. Utilizan las acciones `save_event` y `delete_event`, resultados `ok` o `failed` y motivos acotados como `validation_error` y `not_found`. El actor y la correlación se toman de `ACTOR_ID` y `TRACE_ID`, con respaldo `system` y `m06-verify`.

La política adoptada excluye contraseñas, tokens, claves y payloads sensibles de la auditoría. `AuditTest.sinSecretos` comprueba que una entrada con datos inocuos no contenga `password`, `token`, `secret`, `api_key` ni `private_key`. Además, la verificación del repositorio informó `No versioned secrets detected`. Ambos controles aportan evidencia limitada a las muestras y al alcance del chequeo; no prueban que el servicio sanitice entradas sensibles arbitrarias.

## Recuperación de fixture

El procedimiento utiliza tres eventos sintéticos con los identificadores `RESTORE-FIX-001`, `RESTORE-FIX-002` y `RESTORE-FIX-003`:

1. Cargar la configuración desde `.env`, sin reproducir sus valores en este reporte.
2. Eliminar únicamente los tres identificadores para provocar una falla controlada.
3. Contar los registros restantes y calcular cuántos faltan respecto de los tres esperados.
4. Medir el tiempo de reinserción de los eventos con `schemaVersion: 2`.
5. Consultar los identificadores esperados y comprobar que se recuperaron los tres.
6. Informar las métricas y el resultado del restore.

La prueba de restauración parcial inserta uno de dos documentos esperados y detecta que el conjunto está incompleto. El script usa un fixture de tres documentos; ambos escenarios verifican cantidades respecto de un conjunto conocido.

| Métrica | Definición y cálculo en M06 | Resultado disponible |
| --- | --- | --- |
| RPO | El RPO temporal expresa la ventana de datos que puede perderse respecto del último punto recuperable. El script usa la etiqueta RPO para `3 - before`, que cuenta registros ausentes antes de restaurar. | 3 registros ausentes durante la falla (`3 - 0`). No se midió un RPO temporal. |
| RTO | El script calcula `end - start` en segundos alrededor de la reinserción. Es una medición parcial del tiempo de recuperación. | 1 segundo de reinserción. La duración de Maven no equivale al RTO. |
| SLO | Objetivo temporal del ejercicio: RTO de reinserción menor o igual a 5 segundos. El script imprime `met` o `not_met`. | Cumplido en esta ejecución: `met`, porque `1 <= 5`. También se recuperaron los tres registros esperados. |

La ejecución registró `before = 0` y `after = 3`. La pérdida residual fue de cero registros (`3 - 3`), con recuperación de los tres identificadores esperados. Esta cantidad es distinta de los tres registros ausentes durante la falla. La medición con `date +%s` tiene resolución de segundos y no incluye la detección de la falla ni la consulta de verificación posterior.

## Pruebas

La salida proporcionada de `make verify` registra:

| Suite | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| AccessControlTest | 7 | 0 | 0 | 0 |
| AuditTest | 4 | 0 | 0 | 0 |
| DocumentStoreTest | 7 | 0 | 0 | 0 |
| EventStoreTest | 4 | 0 | 0 | 0 |
| RecoveryTest | 7 | 0 | 0 | 0 |
| RelationalModelTest | 4 | 0 | 0 | 0 |
| TelemetryContractTest | 4 | 0 | 0 | 0 |
| **Total** | **37** | **0** | **0** | **0** |

Las cuatro pruebas de auditoría cubren registro y consulta por `traceId`, agrupación de varias operaciones, filtrado por entidad y ausencia de términos sensibles en una entrada inocua.

Las siete pruebas de recuperación cubren lectura de un fixture insertado, migración repetida, lectura de v1, consulta sin un índice secundario, rechazo de un documento incompleto, fixture sin términos sensibles y detección de recuperación parcial. El caso normal inserta y consulta documentos; no ejecuta por sí mismo un ciclo de respaldo, borrado y restauración.

## Evidencia

La fase de pruebas de la ejecución proporcionada terminó el 11 de octubre de 2026 a las 16:36:31, con zona horaria UTC−06:00. Después se ejecutó el restore del fixture. La salida contiene:

```text
Schema and indexes applied to document_events collection
Matched: 1
Modified: 1
Migration v1 to v2 completed
No versioned secrets detected
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
Running restore fixture...
>> Step 1: Falla controlada (borrando fixture)
>> Step 2: Restore (reinsertando fixture)
>> Step 3: Verificación
>> Step 4: Reporte
Registros esperados: 3
Registros antes del restore: 0
Registros después del restore: 3
RPO (registros perdidos durante la falla): 3
RTO (segundos de recuperación): 1
SLO (<= 5s): met
restore: ok
M06 verification passed
```

Estos mensajes acreditan 37 pruebas aprobadas, la actualización de un documento durante la migración y la recuperación completa del fixture. La salida del script muestra los conteos antes y después, tres registros ausentes durante la falla, un segundo de reinserción y cumplimiento del objetivo de hasta cinco segundos. La pérdida residual después del restore fue de cero registros.

## Límites conocidos

- La recuperación reinserta datos sintéticos conocidos; no restaura un respaldo productivo ni demuestra recuperación a un punto en el tiempo.
- La compatibilidad v1 mostrada se verifica con lectura BSON y una regla aplicada en la prueba. No acredita todos los consumidores o rutas de la aplicación.
- La ausencia de secretos se comprueba mediante términos en muestras inocuas y un chequeo de archivos versionados. Falta evidencia de sanitización frente a entradas sensibles.
- Los fragmentos no muestran atomicidad entre la escritura del evento y su auditoría, ni registro de toda excepción de persistencia.
- Los valores de respaldo de actor y `traceId` pueden agrupar operaciones independientes y limitar la atribución al usuario real.
- La etiqueta RPO del script mide registros ausentes; el RTO mide únicamente reinserción. El SLO temporal no valida por sí solo integridad ni disponibilidad completa.
- El fragmento del script verifica la cantidad restaurada e imprime el cumplimiento temporal; no muestra que exceder los 5 segundos provoque una salida fallida.
- Las consultas sin índices pueden seguir funcionando con mayor costo. No se aportaron mediciones de rendimiento.

## Conclusión

La evidencia de M06 muestra una verificación exitosa con 37 pruebas aprobadas, un documento actualizado por la migración y un restore completo de los tres registros esperados. Los casos presentados cubren evolución mediante `schemaVersion`, consultas de auditoría y detección de recuperación parcial.

Durante la falla faltaron tres registros, valor etiquetado como RPO por el script. La reinserción tardó un segundo y cumplió el SLO de hasta cinco segundos. Tras el restore no quedaron registros faltantes del fixture. Estos resultados corresponden a esta ejecución; las garantías documentadas se limitan al entorno de pruebas y al fixture sintético, y no establecen un RPO temporal ni un tiempo de recuperación completo del servicio.
