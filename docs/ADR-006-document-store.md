# ADR-006 — Diseño del almacén documental de eventos

## Estado

Implementado y verificado para M05 mediante `make verify`, con 26 pruebas aprobadas, sin fallos, errores ni pruebas omitidas.

## Contexto

El proyecto Cloud Data Reliability Lab (CDRL) requiere almacenar eventos de telemetría que comparten campos de identificación, tipo, origen y tiempo, pero cuyo contenido puede variar.

Como continuación de la selección del almacenamiento documental realizada en M04, este hito concreta el modelo de los eventos, las reglas de validación, los índices y las operaciones CRUD en MongoDB.

También se requiere controlar los reintentos para que guardar, actualizar o eliminar repetidamente un evento no produzca registros duplicados ni cambios acumulativos no deseados.

## Decisión

Utilizar la colección `document_events` y organizar la implementación mediante:

- `Event`: modelo del evento.
- `EventRepository`: acceso a MongoDB mediante Spring Data.
- `EventService`: validación y operaciones CRUD.
- `DocumentStoreTest`: pruebas de integración.
- `db/mongo/schema.js`: definición del validador y los índices.

### Modelo del evento

El modelo lógico es:

```json
{
  "eventId": "M05-HAPPY-001",
  "type": "telemetry.created",
  "source": "GPS-001",
  "timestamp": "2026-10-03T20:00:00Z",
  "payload": {
    "speedKmh": 80,
    "distanceKm": 120.5
  },
  "metadata": {
    "schemaVersion": 1,
    "receivedBy": "test"
  }
}
```

| Campo | Tipo | Obligatorio | Propósito |
|-------|------|-------------|-----------|
| `eventId` | String | Sí | Identificador funcional del evento. |
| `type` | String | Sí | Tipo de evento. |
| `source` | String | Sí | Dispositivo u origen. |
| `timestamp` | String | Sí | Marca de tiempo. |
| `payload` | Objeto | Sí | Datos específicos del evento. |
| `metadata` | Objeto | No | Información adicional. |

Se separan dos identificadores:

- `id`, anotado con `@Id`, representa el campo interno `_id` de MongoDB.
- `eventId`, anotado con `@Field("eventId")`, se almacena como un campo independiente y está protegido por un índice único.

Esta separación permite conservar el contrato documental y utilizar `eventId` como referencia para las operaciones del servicio.

El contenido de `payload` permanece flexible; sus propiedades internas no son obligatorias en el esquema actual.

### Validación

Se utilizan dos mecanismos complementarios.

**Validación en Java**

El modelo declara:

- `@NotBlank` para `eventId`, `type`, `source` y `timestamp`.
- `@NotNull` para `payload`.

Antes de guardar, `EventService.save` ejecuta `validator.validate(event)`. Si encuentra incumplimientos, lanza `ConstraintViolationException` y no realiza el guardado.

**Validación en MongoDB**

El archivo `db/mongo/schema.js` utiliza `$jsonSchema` para exigir:

- `eventId`, `type`, `source` y `timestamp` como strings.
- `payload` como objeto.
- `metadata` como objeto, cuando se proporciona.
- `metadata.schemaVersion` como BSON int y `metadata.receivedBy` como string, cuando se proporcionan.

Se establece:

```javascript
validationLevel: "strict",
validationAction: "error"
```

El script crea la colección si no existe o actualiza su validador mediante `collMod`.

La validación Java rechaza strings vacíos o compuestos únicamente por espacios. El esquema MongoDB compartido comprueba los tipos, pero no incorpora esa misma restricción de contenido.

### Índices

Se definen tres índices adicionales al índice automático de `_id`:

| Índice | Configuración | Justificación |
|--------|---------------|---------------|
| `{ eventId: 1 }` | Único | Buscar por identificador funcional e impedir eventos con el mismo `eventId`. |
| `{ type: 1, timestamp: -1 }` | Compuesto | Apoyar consultas por tipo y rango temporal. |
| `{ source: 1, timestamp: -1 }` | Compuesto | Apoyar consultas por origen y rango temporal. |

Los índices compuestos también pueden apoyar consultas con orden temporal descendente compatible. Los métodos actuales del repositorio no solicitan un orden explícito.

Como `timestamp` se almacena como string, se requiere mantener un formato temporal uniforme para que las comparaciones textuales correspondan con el orden cronológico.

### CRUD e idempotencia

El repositorio extiende `MongoRepository<Event, String>` y agrega métodos basados en `eventId`.

| Operación | Método del servicio | Comportamiento |
|-----------|---------------------|----------------|
| Crear | `save(event)` | Valida y guarda un evento nuevo. |
| Leer | `findByEventId(eventId)` | Recupera el evento o devuelve un `Optional` vacío. |
| Actualizar | `save(event)` | Reutiliza el identificador interno del evento existente y guarda su nuevo estado. |
| Eliminar | `deleteByEventId(eventId)` | Elimina el evento si existe. |

También se ofrecen consultas por tipo u origen y rango temporal.

Antes de guardar, el servicio busca un documento con el mismo `eventId`. Si lo encuentra, asigna su `id` interno al objeto recibido antes de ejecutar `repository.save`.

La idempotencia se define respecto al estado final:

- Guardar repetidamente el mismo evento conserva un solo documento.
- Guardar repetidamente la misma actualización conserva los valores actualizados sin duplicar el evento.
- Consultar no modifica los documentos.
- Eliminar repetidamente mantiene el evento ausente.

La eliminación devuelve `true` cuando encuentra y elimina el evento, y `false` cuando ya no existe. La diferencia en la respuesta no modifica la idempotencia del estado final.

Si se guarda información distinta con el mismo `eventId`, la política actual permite actualizar el documento existente; no establece inmutabilidad de los eventos.

## Alternativas consideradas

- Acceso directo mediante el controlador de MongoDB: permite operar sobre documentos, pero exige manejar manualmente el mapeo y las operaciones.
- Spring Data MongoDB: organiza el acceso mediante modelo, repositorio y servicio. Se selecciona esta alternativa.
- Rechazar todos los guardados repetidos: evita duplicados, pero convierte los reintentos en errores y no permite el comportamiento de actualización elegido.
- Utilizar `eventId` únicamente como `_id`: simplifica la identidad, pero no conserva el campo independiente exigido por el esquema y su índice declarado.
- Validar solo en Java: no protege las escrituras realizadas desde otros clientes.
- Validar solo en MongoDB: detecta incumplimientos después de enviar la escritura y no sustituye las reglas del servicio.

## Consecuencias

### Favorables

- Se conserva un contrato común para los eventos.
- Se separa la identidad interna de MongoDB de la identidad funcional.
- Se dispone de creación, consulta, actualización y eliminación.
- Los reintentos secuenciales mantienen el estado esperado.
- El índice único impide almacenar identificadores funcionales repetidos.
- La validación se realiza antes del guardado y también se declara en la colección.

### Costos y límites

- La búsqueda por `eventId` y el guardado son operaciones separadas. Dos creaciones concurrentes podrían provocar un error de clave duplicada.
- No se implementa una recuperación específica de ese conflicto concurrente.
- La comprobación de existencia y la eliminación tampoco constituyen una operación atómica.
- No se valida que `timestamp` represente una fecha real.
- No se restringe la estructura interna de `payload`.
- Los índices requieren almacenamiento y mantenimiento.
- No se han medido rendimiento ni planes de ejecución.
- La limpieza de pruebas utiliza `deleteAll` y requiere un entorno exclusivo de pruebas.

## Verificación

La ejecución del 4 de octubre de 2026, finalizada a las 18:14:09 con zona horaria UTC-06:00, obtuvo:

```text
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
M05 verification passed
```

Las siete pruebas de `DocumentStoreTest` verifican:

1. Creación y lectura por `eventId`.
2. Consulta por tipo y rango temporal.
3. Guardado repetido sin duplicación.
4. Consulta de un identificador inexistente.
5. Actualización idempotente.
6. Eliminación idempotente.
7. Rechazo de un evento inválido mediante validación Java.

El proceso también aplicó el esquema y los índices y ejecutó la revisión de secretos versionados.

La prueba de consulta verifica resultados, no el uso efectivo de un índice. La prueba de evento inválido verifica la validación Java, no una escritura inválida directa contra MongoDB.
