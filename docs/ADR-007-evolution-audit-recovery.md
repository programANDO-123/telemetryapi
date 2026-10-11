# ADR-007 — Evolución documental, auditoría y recuperación

## Estado

Aceptado por el equipo para M06.

## Contexto

La colección `document_events` almacena eventos de telemetría con `eventId`, `type`, `source`, `timestamp` y `payload`. Es necesario evolucionar su estructura sin impedir la lectura de documentos existentes, conservar trazabilidad de las operaciones y verificar la recuperación ante una pérdida controlada de datos.

Los documentos heredados pueden no tener `schemaVersion` en la raíz. El fixture mostrado incluye `metadata.schemaVersion: 1`, pero ese campo anidado no equivale al discriminador de versión definido para M06. Se requiere una regla explícita para distinguir v1 y v2 y una migración que pueda repetirse sin modificar nuevamente documentos ya migrados.

La auditoría debe identificar actor, acción, entidad, resultado y correlación mediante `traceId`, evitando almacenar credenciales o contenido sensible. La recuperación debe ser reproducible con datos sintéticos y acompañarse de indicadores de pérdida y tiempo de recuperación.

## Decisión

1. **Evolución v1/v2.** Se adopta `schemaVersion` en la raíz como discriminador: su ausencia se interpreta como v1 y el valor `2` identifica v2. La lectura de documentos v1 debe seguir siendo compatible. La migración mostrada selecciona documentos sin ese campo mediante `{ schemaVersion: { $exists: false } }` y agrega `schemaVersion: 2` con `$set`, conservando los campos existentes. Una segunda ejecución no debe volver a modificar esos documentos. La migración de M06 agrega el discriminador; no demuestra una transformación adicional del contenido ni una sincronización de `metadata.schemaVersion`.

2. **Integridad documental.** Se mantiene la validación del esquema y el índice único sobre `eventId`, junto con índices de consulta por tipo/tiempo y origen/tiempo. Un documento incompleto debe rechazarse. La ausencia de un índice de consulta puede afectar el rendimiento, aunque la consulta siga devolviendo resultados.

3. **Auditoría operativa.** Se registran `actorId`, `action`, `entity`, `entityId`, `timestamp`, `traceId`, `result` y `reason`. En los fragmentos de `EventService`, el guardado registra `save_event` con resultado `ok` o `failed` por `validation_error`; la eliminación registra `delete_event` con resultado `ok` o `failed` por `not_found`. `ACTOR_ID` y `TRACE_ID` se obtienen del entorno, con valores de respaldo `system` y `m06-verify`. Se permite consultar por `traceId` y por entidad. Los motivos deben limitarse a información operativa y nunca incluir contraseñas, tokens, claves ni payloads sensibles.

4. **Recuperación controlada.** Se utiliza un fixture sintético de tres eventos identificados como `RESTORE-FIX-001`, `RESTORE-FIX-002` y `RESTORE-FIX-003`. El flujo elimina únicamente esos identificadores, los reinserta con `schemaVersion: 2` y comprueba que el conjunto esperado esté completo. Una restauración parcial se considera inconsistente y debe detectarse.

5. **Métricas.** El ejercicio informa pérdida de registros, duración de reinserción y cumplimiento de un objetivo de tiempo de hasta 5 segundos. La variable denominada RPO en el script representa registros ausentes antes del restore, no un RPO temporal. El RTO medido por el script cubre la reinserción y excluye la detección de la falla y la verificación posterior. Para aceptar el ejercicio deben recuperarse los tres registros y cumplirse el objetivo de tiempo; el SLO temporal por sí solo no acredita una recuperación completa.

## Alternativas consideradas

- **Reemplazar todos los documentos y exigir exclusivamente v2.** Descartada porque elimina la compatibilidad con documentos heredados y obliga a una conversión completa antes de continuar operando.
- **Mantener versiones implícitas o usar únicamente `metadata.schemaVersion`.** Descartada para M06 porque la migración y la lectura de compatibilidad mostradas utilizan el campo raíz; mezclar ambos criterios produciría interpretaciones distintas de un mismo documento.
- **Registrar solicitudes completas en la auditoría.** Descartada porque puede copiar secretos o datos sensibles. Se prefieren identificadores, resultados y motivos operativos acotados.
- **Aceptar el restore solo por terminar sin excepción.** Descartada porque una reinserción parcial puede finalizar sin recuperar el conjunto completo. Se exige verificar los identificadores esperados y su cantidad.

## Consecuencias

- La migración es repetible y conserva los datos existentes; las lecturas deben contemplar documentos sin versión explícita.
- El campo raíz y cualquier versión anidada requieren una política consistente en evoluciones futuras. Agregar `schemaVersion: 2` no valida por sí solo toda la semántica de v2.
- La auditoría permite correlacionar operaciones, pero los valores de respaldo del entorno no identifican necesariamente al usuario real ni distinguen cada solicitud.
- La prueba con texto inocuo verifica la ausencia de ciertos términos en esa muestra; no demuestra redacción o rechazo automático de secretos recibidos como entrada.
- El guardado o borrado ocurre antes de registrar su resultado exitoso. Los fragmentos no muestran una transacción que garantice atomicidad entre la operación y su auditoría.
- El restore demuestra recuperación de un fixture conocido, pero no sustituye una estrategia de respaldo de datos productivos, recuperación a un punto en el tiempo o recuperación completa del servicio.
- El objetivo de 5 segundos debe evaluarse junto con la integridad del fixture. Los resultados numéricos deben conservarse como evidencia antes de declarar su cumplimiento.
