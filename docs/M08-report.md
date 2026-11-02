# Reporte M08 — Paginación y rendimiento

## Objetivo

Evaluar la paginación por cursor para el recorrido secuencial de eventos y compararla con la paginación por offset, considerando rendimiento, estabilidad ante inserciones y limitaciones de navegación.

## Alcance

La evaluación comprende la paginación de eventos almacenados en MongoDB, las pruebas de paginación y el benchmark local proporcionados para M08. Se utilizan el script, los fragmentos de código, el log de `make verify` y los resultados JSON compartidos.

El benchmark mide consultas directas a MongoDB; no mide la latencia completa de una petición a la API.

## Paginación por cursor

La implementación ordena los eventos por `timestamp` ascendente. Para continuar el recorrido, utiliza el timestamp del último evento de la página anterior y consulta los eventos cuyo valor sea estrictamente mayor, limitando el tamaño mediante `Pageable`.

El método de repositorio aportado es `findByTimestampGreaterThanOrderByTimestampAsc`. Para offset se utiliza `findAllByOrderByTimestampAsc`.

Las pruebas recorren las páginas desde un cursor inicial nulo hasta recibir una lista vacía. La inserción de un evento posterior al cursor puede incorporarse al recorrido sin desplazar la posición de continuación. La estrategia no garantiza recuperar inserciones anteriores al cursor ni todos los registros cuando existen empates de timestamp.

## Benchmark

| Parámetro | Configuración |
| --- | --- |
| Entorno | Docker Compose local |
| Motor y herramienta | MongoDB mediante `mongosh` |
| Colección | `document_events` |
| Documentos de prueba | 10 000 |
| Muestra | 20 iteraciones por estrategia |
| Tamaño de página | 100 documentos |
| Filtro | `type: "benchmark"` |
| Orden | `timestamp` ascendente |
| Offset | `skip(5000).limit(100)` |
| Cursor | `timestamp > cursorDoc.timestamp`, con `limit(100)` |
| Unidad | Milisegundos |

El script elimina los documentos de tipo `benchmark` existentes e inserta 10 000 nuevos con timestamps separados por un segundo. Ejecuta primero 20 consultas por offset. Después obtiene el documento situado en el desplazamiento 5000 para usar su timestamp como cursor y ejecuta 20 consultas por cursor.

Cada consulta se materializa con `toArray()` y se mide mediante la diferencia de `Date.now()`. La obtención inicial del cursor queda fuera del tiempo medido.

Los tiempos se ordenan de menor a mayor. El script toma el índice 10 para p50 y el índice 19 para p95, usando índices desde cero. Con 20 observaciones, corresponden al undécimo valor y al máximo de la muestra, respectivamente. Se conserva la convención del script proporcionado.

El log de verificación identifica Linux amd64 sobre WSL2, Java 21.0.12.1, Spring Boot 4.1.1 y driver MongoDB 5.8.1. También muestra PostgreSQL y MongoDB disponibles y la aplicación de los índices `eventId_1`, `type_1_timestamp_-1` y `source_1_timestamp_-1`.

## Resultados (p50/p95)

| Estrategia | p50 (ms) | p95 (ms) |
| --- | ---: | ---: |
| Offset | 12 | 27 |
| Cursor | 3 | 5 |

Resultados del JSON actualizado proporcionado:

```json
{
  "sample": 20,
  "fixtures": 10000,
  "environment": "docker-compose local",
  "offset": { "p50": 12, "p95": 27 },
  "cursor": { "p50": 3, "p95": 5 }
}
```

## Comparación con línea base

Se utiliza offset como línea base de la misma evaluación.

| Métrica | Línea base: offset | Cursor | Reducción absoluta | Reducción relativa |
| --- | ---: | ---: | ---: | ---: |
| p50 | 12 ms | 3 ms | 9 ms | 75 % |
| p95 | 27 ms | 5 ms | 22 ms | 81,5 % |

La reducción relativa se calcula como `(offset - cursor) / offset × 100`. La latencia de offset fue 4 veces la de cursor en p50 y 5,4 veces en p95. Estos resultados corresponden al escenario local medido.

## Trade-off

Cursor prioriza la estabilidad del punto de continuación y la menor latencia observada para recorridos secuenciales. Como contrapartida, requiere disponer de un cursor previo y no permite saltar directamente a una página arbitraria por número.

Offset facilita el salto directo, pero las inserciones anteriores al desplazamiento alteran la composición de las páginas. La prueba aportada demuestra que un evento de la primera página se repite en la segunda después de una inserción anterior a los registros existentes.

La estabilidad de cursor tiene límites: no equivale a una instantánea y, al basarse únicamente en timestamp, puede omitir eventos con valores iguales al cursor.

## Pruebas

El log de `make verify` muestra compilación exitosa y estos resultados:

```text
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
M08 verification passed
```

`PaginationTest` ejecutó 4 pruebas sin fallos, errores ni omisiones. El fragmento de código proporcionado describe estos escenarios:

- Recorrido secuencial por cursor de cinco eventos con páginas de dos, comprobando orden y ausencia de duplicados.
- Inserción posterior al cursor entre consultas, comprobando que se recuperan los eventos originales y el nuevo sin duplicados.
- Página por offset fuera de rango y cursor posterior a todos los eventos, ambos con resultado vacío.
- Inserción anterior a los registros existentes con offset, comprobando la repetición de un evento entre páginas y el desplazamiento del último registro fuera de las dos páginas consultadas.

El último caso comprueba una limitación conocida de offset; no representa un fallo de la suite. La inserción se realiza entre consultas y no constituye una prueba de carga concurrente.

## Evidencia

- Script Bash de benchmark: genera los fixtures, mide ambas estrategias y extrae el resultado hacia `artifacts/m08-benchmark.json`.
- JSON actualizado del benchmark: 20 iteraciones por estrategia, 10 000 documentos, offset con p50/p95 de 12/27 ms y cursor con p50/p95 de 3/5 ms.
- Log de `make verify`: compilación exitosa, 45 pruebas aprobadas, incluidas 4 de `PaginationTest`, y cierre `M08 verification passed`.
- Resumen JSON de verificación: `command: "make verify"`, `status: "passed"`, `restore: "ok"` y `benchmarkStatus: "ok"`, con los resultados actualizados del benchmark incluidos.
- Fragmentos de los métodos de repositorio y de las pruebas de paginación proporcionados.

## Límites conocidos

- El benchmark usa 10 000 documentos y una muestra pequeña de 20 observaciones por estrategia. No incluye tiempos individuales ni variabilidad entre ejecuciones.
- El p95 calculado por el script coincide con el máximo observado de la muestra.
- Las consultas por offset se ejecutan antes que las de cursor, sin calentamiento explícito ni alternancia; el estado de caché puede influir en los resultados.
- Las ventanas consultadas difieren en un registro: offset incluye el documento del desplazamiento 5000, mientras cursor comienza después de él.
- Los fixtures tienen timestamps únicos. No se evalúan empates ni escrituras concurrentes durante las mediciones.
- Un cursor basado solo en timestamp puede omitir eventos con el mismo valor si una página termina dentro de ese grupo. Las inserciones anteriores o iguales al cursor quedan fuera de las siguientes páginas.
- No se evalúan modificaciones ni eliminaciones durante el recorrido, ni se ofrece aislamiento de instantánea.
- El benchmark filtra por tipo, mientras que el método de repositorio por cursor mostrado no incluye ese filtro. Los resultados no representan necesariamente todas las consultas de la API.
- No se mide latencia HTTP, carga concurrente, rendimiento sostenido ni otros volúmenes o profundidades de página.
- No se especifican CPU, memoria ni versiones de Docker o del servidor MongoDB. Los nombres de índices aparecen en el log, pero no se incluyen planes de ejecución.
- El orden cronológico de timestamps almacenados como cadenas requiere una representación normalizada.

## Conclusión

En el entorno local evaluado, cursor obtuvo menor latencia que offset: p50 de 3 ms frente a 12 ms y p95 de 5 ms frente a 27 ms, con reducciones del 75 % y del 81,5 %, respectivamente. La verificación reporta 45 pruebas aprobadas y restauración y benchmark con estado `ok`.

Los resultados respaldan el uso de cursor para recorridos secuenciales de eventos, aceptando la pérdida del salto directo por número de página. La estabilidad queda limitada por el orden basado solo en timestamp y por los cambios durante el recorrido. Las mediciones describen el escenario local y no garantizan el mismo rendimiento en producción.
