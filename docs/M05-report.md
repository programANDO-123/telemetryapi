# Reporte M05 — Almacén documental

## Objetivo

Implementar y verificar un almacén documental de eventos en MongoDB con un modelo definido, validación, tres índices y operaciones CRUD con comportamiento idempotente ante reintentos secuenciales.

## Alcance

El hito M05 utiliza la colección `document_events` y comprende:

- Modelo `Event`.
- Repositorio `EventRepository`.
- Servicio `EventService`.
- Validación Java antes del guardado.
- Validador MongoDB definido en `db/mongo/schema.js`.
- Tres índices para identidad y consultas.
- Creación, lectura, actualización y eliminación.
- Consultas por tipo u origen y rango temporal.
- Pruebas de integración mediante `DocumentStoreTest`.
- Ejecución del proceso `make verify`.

Las operaciones se prueban directamente desde el servicio. No se incluye evidencia de una API HTTP.

La configuración de las pruebas utiliza `spring.mongodb.uri` y `spring.mongodb.database`, con valores obtenidos de `MONGO_URI` y `MONGO_DB`.

## Modelo del evento

El evento tiene la siguiente estructura lógica:

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

| Campo | Tipo en Java | Obligatorio | Descripción |
|-------|--------------|-------------|-------------|
| `eventId` | String | Sí | Identificador funcional del evento. |
| `type` | String | Sí | Tipo de evento. |
| `source` | String | Sí | Dispositivo u origen. |
| `timestamp` | String | Sí | Marca de tiempo. |
| `payload` | Map<String, Object> | Sí | Datos específicos del evento. |
| `metadata` | Map<String, Object> | No | Información adicional. |

El modelo incorpora además un campo `id` anotado con `@Id`, que representa `_id` en MongoDB.

El campo `eventId` se almacena de forma independiente mediante `@Field("eventId")`. Así se conserva el campo requerido por el esquema y utilizado por el índice único.

La colección se establece mediante:

```java
@Document(collection = "document_events")
```

## Validación

### Validación Java

Se aplican las siguientes restricciones:

- `@NotBlank` en `eventId`, `type`, `source` y `timestamp`.
- `@NotNull` en `payload`.

`EventService.save` valida el evento antes de enviarlo al repositorio. Cuando existen incumplimientos, lanza `ConstraintViolationException`.

La prueba de fallo declarado construye un evento sin `type`, `timestamp` ni `payload`. Comprueba que se rechace y que no se almacenen documentos.

### Validación MongoDB

El archivo `db/mongo/schema.js` define un `$jsonSchema` con:

- Los cinco campos obligatorios: `eventId`, `type`, `source`, `timestamp` y `payload`.
- Tipos string para los cuatro primeros.
- Tipo object para `payload`.
- Un objeto opcional `metadata`.
- Tipos int y string para `metadata.schemaVersion` y `metadata.receivedBy`, respectivamente.

La configuración utiliza:

```javascript
validationLevel: "strict",
validationAction: "error"
```

El script crea la colección o actualiza su validador si ya existe.

El esquema no valida el formato temporal de `timestamp`, no prohíbe strings vacíos y no define campos internos obligatorios de `payload`.

## Índices

Se definieron y se confirmó la existencia de los siguientes índices en `cdrl_events.document_events`:

| Nombre | Definición | Propósito |
|--------|------------|-----------|
| `eventId_1` | `{ eventId: 1 }`, único | Evitar identificadores funcionales duplicados y apoyar búsquedas por evento. |
| `type_1_timestamp_-1` | `{ type: 1, timestamp: -1 }` | Apoyar consultas por tipo y rango temporal. |
| `source_1_timestamp_-1` | `{ source: 1, timestamp: -1 }` | Apoyar consultas por origen y rango temporal. |

MongoDB mantiene adicionalmente el índice automático `_id_`.

El repositorio incluye consultas mediante:

```java
findByTypeAndTimestampBetween(String type, String from, String to);

findBySourceAndTimestampBetween(String source, String from, String to);
```

La prueba de consulta por tipo verifica que se recuperen dos eventos coincidentes. No utiliza un plan de ejecución para comprobar el índice seleccionado por MongoDB.

Los métodos no establecen un orden explícito de resultados. El uso de strings para el tiempo requiere un formato uniforme.

## CRUD e idempotencia

Las operaciones se implementan en `EventService`, que utiliza `EventRepository`.

| Operación | Método | Comportamiento |
|-----------|--------|----------------|
| Crear | `save(event)` | Valida y guarda un evento nuevo. |
| Leer | `findByEventId(eventId)` | Devuelve el evento o un `Optional` vacío. |
| Actualizar | `save(event)` | Reutiliza el `id` interno del evento existente y guarda los datos recibidos. |
| Eliminar | `deleteByEventId(eventId)` | Elimina el evento si existe y devuelve un booleano. |

### Guardado repetido

Antes de guardar, el servicio busca por `eventId`. Si encuentra un documento, reutiliza su identificador interno.

La prueba guarda dos veces el mismo evento y comprueba que solo exista un documento.

### Actualización idempotente

La prueba crea un evento, cambia su tipo a `telemetry.updated` y guarda dos veces la actualización.

Comprueba que permanezca un único documento con el tipo actualizado.

Este comportamiento permite modificar un evento existente cuando se reciben datos distintos con el mismo `eventId`.

### Eliminación idempotente

El servicio comprueba la existencia del evento y lo elimina cuando está presente.

La prueba solicita la eliminación dos veces:

- La primera llamada devuelve `true`.
- La segunda devuelve `false`.
- El conteo final es cero.

El estado final se mantiene aunque cambie el valor de retorno.

La idempotencia verificada corresponde a repeticiones secuenciales. No se realizaron pruebas concurrentes.

## Pruebas

La clase `DocumentStoreTest` contiene siete casos:

| Caso | Comprobación | Resultado |
|------|--------------|-----------|
| Creación y lectura | Guarda y recupera un evento por `eventId`, comprobando tipo y origen. | Aprobado. |
| Consulta por tipo y rango | Recupera dos eventos coincidentes. | Aprobado. |
| Guardado repetido | Guarda dos veces y conserva un documento. | Aprobado. |
| Ausencia | Devuelve un resultado vacío para un evento inexistente. | Aprobado. |
| Actualización idempotente | Repite la actualización y conserva un registro con el nuevo tipo. | Aprobado. |
| Eliminación idempotente | Repite la eliminación y mantiene el evento ausente. | Aprobado. |
| Fallo declarado | Rechaza un evento inválido mediante `ConstraintViolationException`. | Aprobado. |

Antes de cada prueba se utiliza `service.deleteAll()` para limpiar la colección.

La ejecución completa del proyecto obtuvo:

| Clase | Pruebas | Fallos | Errores | Omitidas |
|-------|---------|--------|---------|----------|
| `AccessControlTest` | 7 | 0 | 0 | 0 |
| `DocumentStoreTest` | 7 | 0 | 0 | 0 |
| `EventStoreTest` | 4 | 0 | 0 | 0 |
| `RelationalModelTest` | 4 | 0 | 0 | 0 |
| `TelemetryContractTest` | 4 | 0 | 0 | 0 |
| **Total** | **26** | **0** | **0** | **0** |

## Evidencia

Se revisó la ejecución de:

```bash
make verify
```

La verificación finalizó el 4 de octubre de 2026 a las 18:14:09, con zona horaria UTC-06:00, después del ajuste que separa `id` y `eventId`.

La salida confirmó:

```text
Schema and indexes applied to document_events collection
No versioned secrets detected
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
M05 verification passed
```

También se comprobó mediante inspección de MongoDB la presencia del validador y de los tres índices declarados en `cdrl_events.document_events`.

El script `scripts/verify_base.sh` aplica `db/mongo/schema.js` sobre la base indicada por `MONGO_DB` y contempla generar `artifacts/m05-verify.json`.

## Límites conocidos

- La búsqueda previa y el guardado no forman una operación atómica. Dos creaciones concurrentes con el mismo `eventId` pueden provocar un error de clave duplicada.
- No se implementa recuperación específica ante ese conflicto concurrente.
- La consulta de existencia y la eliminación se ejecutan por separado.
- La prueba de evento inválido verifica la validación Java, no una escritura inválida directa contra MongoDB.
- La prueba de guardado repetido reutiliza el mismo objeto; no cubre un reintento reconstruido como una nueva instancia de `Event`.
- No se incluyen planes de ejecución ni mediciones de rendimiento de los índices.
- No se presenta una prueba específica de consulta por origen ni de los límites exactos del rango temporal.
- No se valida que `timestamp` contenga una fecha real.
- No se restringe la estructura interna de `payload`.
- No se incluyen pruebas de carga o concurrencia.
- `deleteAll` elimina el contenido de la colección y requiere un entorno exclusivo de pruebas.

## Conclusión

Se implementó un almacén documental con un modelo definido, validación Java y MongoDB, tres índices y operaciones CRUD mediante Spring Data MongoDB.

La separación entre `id` y `eventId` permite conservar el identificador interno de MongoDB y el identificador funcional exigido por el contrato. El servicio reutiliza la identidad interna al actualizar eventos existentes.

La ejecución posterior al ajuste finalizó con 26 pruebas aprobadas, incluidas siete del almacén documental. Los resultados respaldan los casos probados de creación, consulta, guardado repetido, actualización, eliminación y rechazo de datos inválidos, con los límites de cobertura descritos.
