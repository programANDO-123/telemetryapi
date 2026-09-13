# Reporte M02 — Modelo relacional operativo

## Objetivo

Diseñar y dejar operativo el modelo relacional correspondiente a M02.
La entrega busca ampliar el contrato de datos de M01 para representar las entidades principales de la aplicación de telemetría, establecer las relaciones entre ellas, aplicar restricciones de integridad y contar con datos sintéticos para realizar las pruebas.
También se busca que la estructura pueda reproducirse mediante migraciones idempotentes y que el modelo pueda consultarse utilizando relaciones entre vehículos, conductores, viajes y lecturas de telemetría.
Además, se implementaron y ejecutaron cuatro escenarios de prueba en `RelationalModelTest`: caso normal, caso vacío, caso límite y fallo declarado, utilizando consultas parametrizadas mediante JDBC.

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
* Clase `RelationalModelTest` con los cuatro escenarios de prueba de M02 implementados.
* Pruebas parametrizadas mediante `PreparedStatement`.
* Evidencia correspondiente a la entrega.
* Verificación del funcionamiento del proyecto mediante `make verify`.
* Ejecución del entorno mediante `make run`, comprobando el inicio correcto de PostgreSQL, DynamoDB y la aplicación Spring Boot.

Lo que no entró en esta entrega:
* Implementación de endpoints HTTP.
* Implementación de lógica de negocio de la API.
* Implementación de métricas adicionales fuera de la vista definida.
* Despliegue en AWS.
* Implementación de JPA, Flyway o Testcontainers, ya que no forman parte de lo necesario para esta etapa.

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
La relación entre conductores y vehículosse representa mediante `driver_vehicle_assignments`, que además registra la fecha y hora de la asignación.
Los viajes se representan mediante `trips`. Cada viaje tiene un vehículo, un conductor, una fecha de inicio, una fecha de finalización opcional, un estado y el número de segundos de inactividad.
El estado del viaje está limitado a tres valores:
* `in_progress`
* `completed`
* `cancelled`
También se estableció que `idle_seconds` no puede ser negativo y que `ended_at`, cuando se proporciona, debe ser posterior a `started_at`.
Para conectar el modelo con las lecturas existentes de M01 se agregó `trip_id` a `telemetry_readings`, creando una relación mediante llave foránea.
Las alertas se relacionan con `telemetry_readings` medante `reading_id` y tienen una restricción para que su severidad solamente pueda ser `low`, `medium` o `high`.
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

Se implementaron cuatro escenarios:
* **Caso normal:** se realiza una consulta parametrizada para buscar un vehículo mediante su VIN. La prueba comprueba que exista un resultado y valida que la marca, modelo y año correspondan al vehículo esperado.
* **Caso vacío:** se realiza una consulta parametrizada utilizando un VIN inexistente. La prueba comprueba que la consulta se ejecute correctamente y que no devuelva registros.
* **Caso límite:** se realiza una inserción parametrizada utilizando `year = 1900`, que corresponde al límite mínimo permitido por la restricción del modelo. La prueba comprueba que el registro sea insertado correctamente.
* **Fallo declarado:** se intenta insertar un vehículo utilizando `year = 1899`, valor que no cumple la restricción `year >= 1900`. La prueba utiliza `assertThrows(SQLException.class, ...)` para comprobar que PostgreSQL rechace el dato inválido.
Las consultas e inserciones de las pruebas utilizan `PreparedStatement`, permitiendo enviar los valores como parámetros en lugar de incorporarlos directamente en las instrucciones SQL.
La verificación del proyecto mediante:
```text
make verify
```
terminó con:
```text
Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```
El resultado corresponde a las cuatro pruebas de `RelationalModelTest` y las cuatro pruebas de `TelemetryContractTest`.
El proceso terminó correctamente con:
```text
BUILD SUCCESS
```
y posteriormente:
```text
M02 verification passed
```
Por lo tanto, los cuatro escenarios específicos de `RelationalModelTest` fueron implementados y ejecutados correctamente.
También se ejecutó:

```text
make run
```

El comando inició correctamente los contenedores de PostgreSQL y DynamoDB. Posteriormente, la aplicación Spring Boot inició utilizando Java 21 y Tomcat quedó disponible en el puerto 8080.

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

Los comandos registrados corresponden a las actividades utilizadas para preparar, verificar y ejecutar el proyecto.
La ejecución de `make verify` confirmó que el modelo y las pruebas implementadas funcionan correctamente, obteniendo 8 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas.
El archivo de evidencia todavía contiene información que deberá actualizarse cuando se genere el tag correspondiente a la entrega, específicamente el valor de `commitSha` si continúa registrado como `PENDING_TAG`.

## Límites conocidos

* La evidencia `evidence/m02-relational-model.json` todavía puede contener información pendiente relacionada con la identificación definitiva del commit o tag de entrega.
* El campo `commitSha` aparece como `PENDING_TAG` hasta generar el tag correspondiente.
* El alcance continúa limitado al modelo relacional y no incluye una API HTTP ni funcionalidades completas de negocio.
* Los datos del seed son datos sintéticos utilizados para las pruebas y no representan información real.
* Las pruebas de `RelationalModelTest` validan los escenarios definidos para M02, pero no sustituyen pruebas de endpoints HTTP o de lógica de negocio, ya que estas se encuentran fuera del alcance de esta entrega.
* La vista `driving_patterns` se limita a la información definida para los viajes completados y sus lecturas asociadas.

## Conclusión

M02 amplía el contrato de datos establecido en M01 mediante un modelo relacional operativo para vehículos, conductores, asignaciones, viajes y alertas.
Las relaciones mediante llaves foráneas permiten conectar las diferentes entidades y relacionar las lecturas de telemetría con los viajes. Las restricciones definidas en PostgreSQL ayudan a mantener la integridad de los datos y a rechazar valores que no cumplen las reglas establecidas.
También se incorporaron datos sintéticos y una vista `driving_patterns` para facilitar las consultas sobre los viajes completados.
Como parte de la validación, se implementaron los cuatro escenarios definidos en `RelationalModelTest`: caso normal, caso vacío, caso límite y fallo declarado. Las pruebas utilizan consultas parametrizadas mediante `PreparedStatement`.
La ejecución de `make verify` fue satisfactoria, con 8 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas. El proceso terminó con `BUILD SUCCESS` y `M02 verification passed`.
Además, mediante `make run` se comprobó que PostgreSQL y DynamoDB iniciaran correctamente y que la aplicación Spring Boot pudiera ejecutarse utilizando Java 21 y Tomcat en el puerto 8080.
En general, M02 deja establecida y validada la estructura relacional necesaria para continuar con las siguientes etapas del proyecto, manteniendo el stack y la base de datos definidos desde M01.
