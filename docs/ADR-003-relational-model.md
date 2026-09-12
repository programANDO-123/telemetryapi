# ADR-003 — Modelo relacional operativo

## Estado

Aceptado por el equipo para M02.

## Contexto

En M02 el objetivo es pasar del contrato de datos de M01 a un modelo relacional operativo que represente las principales entidades de la aplicación de telemetría.

En M01 la base tenía el contrato inicial para `devices` y `telemetry_readings`. Para esta entrega necesitábamos agregar las entidades relacionadas con vehículos, conductores y viajes, además de establecer las relaciones necesarias para conectar los viajes con las lecturas de telemetría.

También necesitábamos que la estructura pudiera reproducirse de manera sencilla. Por eso las migraciones y los datos de prueba debían ser idempotentes, es decir, poder ejecutarse nuevamente sin provocar errores por elementos o registros que ya existen.

El modelo también debe aplicar las reglas de integridad directamente en PostgreSQL mediante llaves primarias, llaves foráneas, restricciones `UNIQUE`, `NOT NULL` y `CHECK`.

## Decisión

El modelo relacional de M02 se implementa sobre PostgreSQL 16, manteniendo el stack definido en M01.

Se agregan las siguientes tablas:

* `vehicles`: contiene los vehículos registrados, su VIN, marca, modelo, año y el dispositivo asociado.
* `drivers`: contiene la información de los conductores, incluyendo número de licencia, CURP y nombre.
* `driver_vehicle_assignments`: representa la relación entre conductores y vehículos y registra cuándo se realizó cada asignación.
* `trips`: representa los viajes realizados por un conductor en un vehículo.
* `alerts`: almacena las alertas relacionadas con lecturas de telemetría.

Además, `telemetry_readings` se amplía con la columna `trip_id`, que funciona como llave foránea hacia `trips`. De esta forma una lectura de telemetría puede quedar relacionada con el viaje al que pertenece.

El modelo utiliza UUID como identificadores de las entidades principales y `TIMESTAMPTZ` para los datos que representan fechas y horas.

Se agregan restricciones para mantener la integridad de los datos:

* El `vin` de un vehículo es obligatorio y único.
* El año del vehículo debe ser mayor o igual a 1900.
* El `device_id` asociado a un vehículo es único y referencia a `devices`.
* El número de licencia del conductor es obligatorio y único.
* La CURP es obligatoria y única.
* Una asignación requiere que existan el conductor y el vehículo relacionados.
* Un viaje requiere un vehículo y un conductor existentes.
* El estado de un viaje solamente puede ser `in_progress`, `completed` o `cancelled`.
* `idle_seconds` no puede ser negativo.
* La fecha de finalización de un viaje debe ser posterior a la fecha de inicio cuando existe.
* Una alerta debe estar relacionada con una lectura de telemetría.
* La severidad de una alerta solamente puede ser `low`, `medium` o `high`.

Para permitir que las migraciones se puedan volver a ejecutar se utilizan instrucciones como `CREATE TABLE IF NOT EXISTS` y `ADD COLUMN IF NOT EXISTS`.

El seed utiliza `ON CONFLICT DO NOTHING`, por lo que los datos sintéticos pueden cargarse nuevamente sin generar duplicados.

También se crea la vista `driving_patterns`. Esta vista utiliza los viajes completados y sus lecturas de telemetría para obtener por conductor el total de viajes, la velocidad promedio y la distancia total registrada.

Las pruebas de M02 se preparan en `RelationalModelTest` y se conectan directamente a PostgreSQL mediante JDBC utilizando las variables de entorno definidas para la conexión.

## Alternativas consideradas

* **Mantener únicamente las tablas de M01.** Se descartó porque no permite representar de forma adecuada vehículos, conductores, asignaciones y viajes, que son parte del modelo requerido en M02.

* **Guardar vehículos, conductores y viajes en una sola tabla.** Se descartó porque mezclar entidades diferentes produciría duplicación de información y dificultaría mantener las relaciones.

* **No utilizar llaves foráneas.** Se descartó porque permitiría registrar viajes, asignaciones o alertas que hagan referencia a entidades inexistentes.

* **No aplicar restricciones en la base de datos.** Se descartó porque las reglas de integridad quedarían solamente en la aplicación y no estarían protegidas directamente por PostgreSQL.

* **Crear las tablas sin hacer las migraciones idempotentes.** Se descartó porque una segunda ejecución podría provocar errores al intentar crear tablas o columnas que ya existen.

* **Cargar datos de prueba sin control de conflictos.** Se descartó porque volver a ejecutar el seed podría generar registros duplicados o errores de clave única.

## Consecuencias

* El proyecto pasa de tener únicamente el contrato inicial de telemetría a contar con un modelo relacional que representa vehículos, conductores y viajes.
* Las relaciones mediante llaves foráneas mantienen la integridad entre las diferentes entidades.
* Las restricciones `CHECK`, `UNIQUE` y `NOT NULL` permiten que PostgreSQL rechace datos que no cumplen las reglas definidas.
* Las lecturas de telemetría pueden asociarse con un viaje mediante `trip_id`.
* Los viajes pueden almacenar su estado, duración e información de inactividad.
* Las alertas quedan relacionadas con las lecturas de telemetría.
* La vista `driving_patterns` permite consultar información resumida de los viajes completados.
* Las migraciones pueden ejecutarse nuevamente sin intentar crear elementos que ya existen.
* El seed puede repetirse sin duplicar los registros existentes.
* Las pruebas de M02 están preparadas para validar un caso normal, un caso vacío, un caso límite y un fallo declarado.
* La implementación específica de esos cuatro casos de prueba queda pendiente en `RelationalModelTest`, ya que actualmente los métodos contienen `TODO`.
