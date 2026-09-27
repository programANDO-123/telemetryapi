# ADR-005 — Decisión arquitectónica NoSQL

## Estado

Aceptado por el equipo para M04.

## Contexto

Para M04 se requiere seleccionar un modelo de almacenamiento NoSQL adecuado para los eventos generados por dispositivos de telemetría del proyecto Cloud Data Reliability Lab (CDRL).

Los eventos requieren almacenar información como `ts`, `deviceId` y `type`, además de permitir consultas por dispositivo y por rangos de tiempo. La implementación realizada utiliza MongoDB como almacenamiento orientado a documentos.

La solución de M04 incorpora MongoDB 7 mediante Docker Compose y utiliza el driver oficial de MongoDB para Java versión 5.2.1. La configuración utiliza `MONGO_URI=mongodb://localhost:27017` y la base de datos `cdrl_events`.

La implementación de pruebas contempla:

* Inserción de eventos.
* Consulta de eventos por `deviceId` y rango de `ts`.
* Validación del contrato mínimo de un evento.
* Consulta de un rango sin eventos.
* Validación de fallo ante una conexión inválida.

También se crearon índices para los campos `ts`, `deviceId` y `type`.

La verificación de M04 se ejecutó mediante `make verify`. MongoDB inició correctamente y las pruebas de `EventStoreTest` finalizaron con 4 pruebas ejecutadas, 0 fallos y 0 errores. En conjunto, la verificación terminó con 19 pruebas ejecutadas, 0 fallos, 0 errores y el estado `BUILD SUCCESS`.

## Opciones consideradas

* **Document:** Modelo basado en documentos, adecuado para representar eventos de telemetría como documentos independientes y realizar consultas por atributos como `deviceId`, `ts` y `type`.

* **Graph:** Modelo basado en nodos y relaciones, orientado principalmente a consultas donde las relaciones entre entidades constituyen el elemento central.

* **Column:** Modelo orientado a columnas, adecuado para grandes volúmenes de datos distribuidos y cargas de escritura/consulta que puedan aprovechar este tipo de organización.

* **Object:** Modelo orientado al almacenamiento de objetos, apropiado principalmente para archivos y objetos grandes, pero menos alineado con las consultas estructuradas requeridas para los eventos.

* **Alternativa descartada:** Graph, Column y Object se descartan para M04 porque el caso de uso requiere almacenar y consultar eventos estructurados mediante atributos y rangos temporales, mientras que la implementación y las pruebas realizadas están orientadas directamente a un almacén documental.

## Decisión

Se selecciona **Document** como modelo NoSQL para M04 y se utiliza **MongoDB** como tecnología de implementación.

La decisión se fundamenta en que el modelo documental permite representar cada evento como un documento con campos como `ts`, `deviceId` y `type`, además de facilitar las consultas por dispositivo y rango temporal requeridas por las pruebas.

La implementación utiliza MongoDB 7 y el driver MongoDB Sync 5.2.1. Las pruebas crean índices sobre `ts`, `deviceId` y `type`, y verifican la inserción y consulta de eventos, la validación del contrato mínimo, los rangos sin resultados y el comportamiento ante una conexión inválida.

La evidencia disponible muestra que la implementación fue verificada correctamente mediante `make verify`, con 19 pruebas ejecutadas y todas aprobadas.

## Consecuencias

* Se incorpora MongoDB como almacenamiento NoSQL documental para los eventos de M04.
* Los eventos pueden representarse como documentos con campos `ts`, `deviceId` y `type`.
* Las consultas por dispositivo y rango temporal quedan contempladas por la implementación.
* Se utilizan índices sobre `ts`, `deviceId` y `type` para apoyar las consultas esperadas.
* Se agrega MongoDB 7 al entorno Docker Compose del proyecto.
* El proyecto mantiene PostgreSQL para el modelo relacional de las etapas anteriores y MongoDB para el almacenamiento documental de M04.
* La solución requiere mantener la configuración de conexión de MongoDB mediante variables de entorno.
* La verificación de M04 queda respaldada por las pruebas automatizadas, con 19 pruebas exitosas y sin fallos.
