# Reporte M02 — Modelo relacional operativo

## Objetivo

Diseñar y dejar operativo el modelo relacional correspondiente a M02.

La entrega busca ampliar el contrato de datos de M01 para representar las entidades principales de la aplicación de telemetría, establecer las relaciones entre ellas, aplicar restricciones de integridad y contar con datos sintéticos para realizar las pruebas.

También se busca que la estructura pueda reproducirse mediante migraciones idempotentes y que el modelo pueda consultarse utilizando relaciones entre vehículos, conductores, viajes y lecturas de telemetría.

## Alcance

Lo que sí entró en esta entrega:

* Tabla `vehicles` para representar los vehículos.
* Tabla `drivers` para representar los conductores.
* Tabla `driver_vehicle_assignments` para relacionar conductores y vehículos.
* Tabla `trips` para representar los viajes.
* Columna `trip_id` en `telemetry_readings` para relacionar las lecturas con los viajes.
* Tabla `alerts` para registrar alertas asociadas a lecturas.
* Vista `driving_patterns` para obtener información resumida de los viajes completados.
* Restricciones de integridad mediante `PRIMARY KEY`, `FOREIGN KEY`, `UNIQUE`, `NOT NULL` y `CHECK`.
* Migración idempotente del modelo.
* Seed sintético para vehículos, conductores, asignaciones y viajes.
* Clase `RelationalModelTest` preparada para las pruebas de M02.
* Evidencia correspondiente a la entrega.

Lo que no entró en esta entrega:

* Implementación de endpoints HTTP.
* Implementación de lógica de negocio de la API.
* Implementación de métricas adicionales fuera de la vista definida.
* Despliegue en AWS.
* Implementación de JPA, Flyway o Testcontainers, ya que no forman parte de lo necesario para esta etapa.
* Implementación definitiva de los cuatro casos de `RelationalModelTest`, que permanecen pendientes.

## Entregables

| Qué                  | Dónde                                                      |
| -------------------- | ---------------------------------------------------------- |
| Migración del modelo | `db/migrations/`                                           |
| Seed del modelo      | `db/seed/`                                                 |
| Pruebas del modelo   | `src/test/java/com/telemetry/api/RelationalModelTest.java` |
| ADR del modelo       | `docs/ADR-003-relational-model.md`                         |
| Reporte M02          | `docs/M02-report.md`                                       |
| Evidencia            | `evidence/m02-relational-model.json`                       |

El modelo incorpora las tablas `vehicles`, `drivers`, `driver_vehicle_assignments`, `trips` y `alerts`, además de la relación `trip_id` en `telemetry_readings`.

## Decisiones

Las decisiones del modelo están documentadas en ADR-003.

Se decidió mantener PostgreSQL como base de datos, siguiendo el stack establecido en M01.

Los vehículos se identifican mediante `vehicle_id` y se relacionan opcionalmente con un dispositivo mediante `device_id`.

Los conductores utilizan `driver_id` como identificador. Tanto el número de licencia como la CURP se definieron como valores únicos.

La relación entre conductores y vehículos se representa mediante `driver_vehicle_assignments`, que además registra la fecha y hora de la asignación.

Los viajes se representan mediante `trips`. Cada viaje tiene un vehículo, un conductor, una fecha de inicio, una fecha de finalización opcional, un estado y el número de segundos de inactividad.

El estado del viaje está limitado a tres valores:

* `in_progress`
* `completed`
* `cancelled`

También se estableció que `idle_seconds` no puede ser negativo y que `ended_at`, cuando se proporciona, debe ser posterior a `started_at`.

Para conectar el modelo con las lecturas existentes de M01 se agregó `trip_id` a `telemetry_readings`, creando una relación mediante llave foránea.

Las alertas se relacionan con `telemetry_readings` mediante `reading_id` y tienen una restricción para que su severidad solamente pueda ser `low`, `medium` o `high`.

Finalmente, se creó la vista `driving_patterns`, que toma los viajes con estado `completed` y sus lecturas asociadas para obtener:

* Conductor.
* Total de viajes.
* Velocidad promedio.
* Distancia total.

## Pruebas

Para M02 se creó `RelationalModelTest`.

La prueba establece una conexión JDBC con PostgreSQL utilizando las variables:

* `POSTGRES_HOST`
* `POSTGRES_PORT`
* `POSTGRES_DB`
* `POSTGRES_USER`
* `POSTGRES_PASSWORD`

Se definieron cuatro escenarios:

* **Caso normal:** una consulta parametrizada debe devolver datos.
* **Caso vacío:** una consulta parametrizada debe poder ejecutarse cuando no existen registros que coincidan con los parámetros.
* **Caso límite:** se debe comprobar un valor ubicado en el límite de una restricción del modelo.
* **Fallo declarado:** se debe comprobar que PostgreSQL rechace un dato inválido que viole una restricción.

Actualmente los cuatro métodos están creados, pero su implementación aparece como `TODO`. Por lo tanto, estos escenarios están definidos pero todavía no deben reportarse como pruebas ejecutadas.

La verificación registrada del proyecto mediante:

```text
make verify
```

terminó con:

```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

Esto indica que las ocho pruebas ejecutadas por `make verify` terminaron correctamente.

Este resultado no debe confundirse con las cuatro pruebas específicas de `RelationalModelTest`, ya que estas todavía están pendientes de implementación.

## Evidencia

La evidencia de M02 se encuentra en:

```text
evidence/m02-relational-model.json
```

El archivo contempla la identificación de la entrega mediante:

```json
{
  "assignmentId": "m02-relational-model",
  "commitSha": "PENDING_TAG",
  "commands": [
    "make setup",
    "make verify",
    "make run"
  ]
}
```

En el estado actual, la evidencia todavía marca el modelo relacional como pendiente:

```text
relationalModel: pending
```

También se registra que la verificación de la base inicial fue correcta:

```text
status: starter_base_valid
scope: structure_and_contract_only
```

Por lo tanto, el archivo de evidencia necesita actualizarse con los resultados definitivos de M02 una vez que se completen las pruebas específicas y se genere el tag correspondiente.

## Límites conocidos

* Las cuatro pruebas específicas de `RelationalModelTest` todavía contienen `TODO`.
* No se cuenta todavía con los resultados de los casos normal, vacío, límite y fallo declarado de M02.
* El archivo `evidence/m02-relational-model.json` todavía contiene información pendiente.
* El campo `commitSha` todavía aparece como `PENDING_TAG`.
* La evidencia todavía indica `relationalModel: pending`.
* La verificación de `make verify` demuestra que las ocho pruebas actualmente ejecutadas pasaron, pero no sustituye las pruebas específicas de M02.
* El alcance continúa limitado al modelo relacional y no incluye una API HTTP ni funcionalidades completas de negocio.
* Los datos del seed son datos sintéticos utilizados para las pruebas y no representan información real.

## Conclusión

M02 amplía el contrato de datos establecido en M01 mediante un modelo relacional operativo para vehículos, conductores, asignaciones, viajes y alertas.

Las relaciones mediante llaves foráneas permiten conectar las diferentes entidades y relacionar las lecturas de telemetría con los viajes. Las restricciones definidas en PostgreSQL ayudan a mantener la integridad de los datos y a rechazar valores que no cumplen las reglas establecidas.

También se incorporaron datos sintéticos y una vista `driving_patterns` para facilitar las consultas sobre los viajes completados.

La ejecución registrada de `make verify` fue satisfactoria, con 8 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas.

Sin embargo, los cuatro escenarios específicos definidos en `RelationalModelTest` todavía están pendientes de implementación, por lo que la validación completa de M02 aún no debe considerarse terminada.

En general, M02 deja establecida la estructura relacional necesaria para continuar con las siguientes etapas del proyecto, manteniendo el stack y la base de datos definidos desde M01.
