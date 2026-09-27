# M04 — Matriz ponderada de decisión NoSQL

## Criterios y pesos

| Criterio                   | Peso     | Document | Graph    | Column   | Object   |
| -------------------------- | -------- | -------- | -------- | -------- | -------- |
| Consultas esperadas        | 30%      | 5        | 2        | 3        | 2        |
| Escala y escritura         | 25%      | 4        | 3        | 5        | 4        |
| Consistencia requerida     | 20%      | 4        | 4        | 4        | 2        |
| Costo operativo            | 10%      | 4        | 3        | 4        | 5        |
| Simplicidad de integración | 15%      | 5        | 2        | 3        | 2        |
| **Total ponderado**        | **100%** | **4.40** | **2.80** | **3.85** | **2.90** |

> Escala utilizada: 1 = menor adecuación y 5 = mayor adecuación para los requerimientos de M04.

## Evidencia o hipótesis por criterio

**Consultas esperadas:**
El caso de uso requiere insertar eventos y consultarlos mediante `deviceId` y rangos de tiempo utilizando `ts`. El modelo documental se adapta directamente a esta estructura. La prueba normal de M04 realiza una inserción y posteriormente una consulta por `deviceId` y rango temporal.

**Escala y escritura:**
Los eventos de telemetría pueden generarse de forma continua y almacenarse como documentos independientes. El modelo documental permite agregar eventos sin requerir una estructura relacional compleja.

**Consistencia requerida:**
Para M04 se requiere que los eventos insertados puedan ser consultados correctamente y que los datos necesarios del evento estén presentes. La prueba de evento incompleto verifica el contrato mínimo compuesto por `ts`, `deviceId` y `type`.

**Costo operativo:**
La implementación utiliza MongoDB 7 mediante Docker Compose, lo que permite ejecutar el almacenamiento localmente dentro del entorno de desarrollo y pruebas. La configuración utiliza el puerto `27017`.

**Simplicidad de integración:**
La integración con Java se realiza mediante el driver oficial de MongoDB Sync 5.2.1. El código utiliza `MongoClient`, `MongoDatabase`, `MongoCollection<Document>` y `Document`, permitiendo representar directamente los eventos como documentos.

## Selección

**Document** es el modelo seleccionado para M04.

La selección se relaciona directamente con las consultas esperadas del proyecto: almacenar eventos de telemetría y recuperarlos por dispositivo y rango temporal. La implementación utiliza MongoDB y documentos con los campos `ts`, `deviceId` y `type`.

Como evidencia de la selección, `EventStoreTest` ejecuta cuatro pruebas relacionadas con el almacenamiento documental: inserción y consulta por rango, validación de evento incompleto, consulta de un rango vacío y fallo ante una conexión inválida. Las cuatro pruebas finalizaron correctamente.
Además, la verificación completa de M04 terminó con:

* **19 pruebas ejecutadas**
* **0 fallos**
* **0 errores**
* **0 pruebas omitidas**
* **BUILD SUCCESS**
* **M04 verification passed**
