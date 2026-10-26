# ADR-008 — Versionado y migración compatible

## Estado

Aceptado por el equipo para M07.

## Contexto

La colección `document_events` contiene documentos de eventos que deben evolucionar para incorporar información de firmware y calidad sin impedir el uso de documentos existentes.

Los documentos anteriores pueden no incluir un campo de versión. Exigir que todos tengan inmediatamente el formato nuevo dificultaría su lectura y obligaría a coordinar una conversión completa antes de utilizar la aplicación.

Se requiere una estrategia que permita distinguir versiones, mantener documentos v1 y v2 en la misma colección y regresar al formato anterior cuando sea necesario.

El procedimiento debe integrarse con la verificación automatizada del proyecto y conservar las comprobaciones de recuperación incorporadas en M06.

## Decisión

Se adopta versionado explícito mediante `schemaVersion` y compatibilidad con documentos antiguos sin dicho campo.

Las reglas son:

- Un documento sin `schemaVersion` se interpreta como v1.
- Al guardar un evento sin versión, `EventService` asigna `schemaVersion: 1`.
- Al consultar mediante `findByEventId`, una versión nula se normaliza a `1`.
- Los eventos v1 no conservan valores de `firmwareVersion` ni `qualityScore` al pasar por el guardado del servicio.
- Los eventos v2 utilizan `schemaVersion: 2` e incorporan `firmwareVersion` y `qualityScore`.
- Al guardar v2 con esos campos nulos, se asignan `"unknown"` y `0.0`, respectivamente.
- Los documentos v1 y v2 pueden coexistir en `document_events`.
- La actualización de un evento existente reutiliza su identificador interno a partir de `eventId`.

La conversión a v2 se realiza mediante `db/mongo/migrate_v1_to_v2.js`.

El retorno al formato v1 se realiza mediante `db/mongo/rollback_v2_to_v1.js`, que selecciona documentos con `schemaVersion: 2` y elimina `schemaVersion`, `firmwareVersion` y `qualityScore`.

El rollback es idempotente por su filtro: después de revertir un documento, este deja de cumplir la condición de selección.

Se incorpora a `make verify` el ciclo v1 → v2 → v1 → v2 con un documento sintético, junto con las pruebas automatizadas y el restore de fixture.

El rollback recupera la estructura compatible con v1, pero no conserva los valores exclusivos de v2. Si es necesario recuperarlos, se requiere un respaldo previo.

## Alternativas consideradas

- Migrar todos los documentos a v2 y retirar inmediatamente el soporte de v1. Simplifica el manejo de versiones, pero exige una conversión completa y reduce la compatibilidad con datos anteriores.
- Mantener colecciones separadas para v1 y v2. Aísla cada formato, pero aumenta la complejidad de consultas, actualizaciones y mantenimiento.
- Inferir la versión únicamente por la presencia de campos. Evita un discriminador explícito, pero introduce ambigüedad cuando los campos son opcionales o están incompletos.
- Conservar exclusivamente v1. Evita la migración, pero no permite incorporar los nuevos campos bajo un contrato de versión explícito.

## Consecuencias

- Se mantiene compatibilidad con documentos antiguos sin versión y se permite la coexistencia de v1 y v2.
- La evolución del formato queda identificada mediante un campo explícito.
- El servicio incorpora reglas de normalización y valores por defecto que deberán mantenerse en futuras versiones.
- La actualización probada de v1 a v2 conserva la identidad del documento y evita duplicados.
- El rollback excluye documentos v1, pero elimina información exclusiva de v2 de todos los documentos seleccionados.
- La verificación proporcionada registra 41 pruebas aprobadas, incluidas cuatro de versionado, y un restore con resultado `ok`.
- El ciclo automatizado comprueba migración, rollback y nueva migración; comprobar idempotencia de la migración requiere además ejecutarla consecutivamente sobre documentos ya migrados.
- La referencia definitiva del commit o tag y los resultados del archivo de evidencia deben registrarse para completar la trazabilidad.
