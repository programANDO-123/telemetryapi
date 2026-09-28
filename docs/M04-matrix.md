# M04 — Matriz ponderada de decisión NoSQL

## Objetivo

Seleccionar una familia de almacenamiento NoSQL adecuada para los eventos operativos y de telemetría del proyecto Cloud Data Reliability Lab (CDRL), mediante una matriz ponderada y evidencia reproducible.
La decisión considera las consultas requeridas, la escala y escritura, la consistencia, el costo y operación, y la simplicidad de integración.
El escenario contempla eventos sintéticos que representan actividad operativa, señales, estados y fallos. La estructura básica de un evento es:
```text
{ id, type, ts, deviceId }
```
Las consultas esperadas consideran principalmente información por rango, estado y origen.
La selección no se realiza por popularidad o por preferencia tecnológica, sino a partir de las características del escenario y de la evidencia obtenida durante la implementación.

## Criterios y pesos

| Criterio                   |     Peso | Document |    Graph |   Column |   Object |
| -------------------------- | -------: | -------: | -------: | -------: | -------: |
| Consultas esperadas        |      30% |        4 |        2 |        3 |        1 |
| Escala y escritura         |      25% |        3 |        2 |        4 |        3 |
| Consistencia requerida     |      15% |        3 |        3 |        3 |        2 |
| Costo operativo            |      15% |        3 |        2 |        3 |        4 |
| Simplicidad de integración |      15% |        4 |        2 |        3 |        3 |
| **Total ponderado**        | **100%** | **3.40** | **2.20** | **3.35** | **2.55** |

> Escala utilizada: 1 = menor adecuación y 5 = mayor adecuación para los requerimientos de M04.

> Los puntajes fueron establecidos para este escenario y no corresponden a los valores de ejemplo mostrados en la presentación.
## Justificación de los puntajes
### Consultas esperadas — 30%
El escenario CDRL requiere consultar eventos mediante características como rango temporal, estado y origen.
El modelo documental permite representar los eventos como documentos y consultar sus atributos sin requerir que el problema principal sea recorrer relaciones entre entidades.
**Document: 4/5.**
Graph recibe una puntuación menor porque el escenario no plantea consultas basadas principalmente en recorridos de relaciones o caminos entre nodos.
**Graph: 2/5.**
Column puede ser adecuado para consultas por claves o rangos a gran volumen, pero el escenario contempla diferentes atributos de los eventos y no se limita a un patrón de consulta exclusivamente orientado a columnas.
**Column: 3/5.**
Object recibe una puntuación baja porque un object store está orientado principalmente a archivos, blobs o almacenamiento histórico, no a consultas estructuradas frecuentes sobre eventos.
**Object: 1/5.**

### Escala y escritura — 25%
El escenario contempla crecimiento de eventos y cargas de escritura generadas por la actividad operativa.
Además, el análisis realizado en Semana 4 contempla un escenario de aproximadamente 250,000 eventos diarios.
Document permite manejar grandes cantidades de documentos y escalar la colección conforme aumente la información.
**Document: 3/5.**
Column obtiene una puntuación mayor debido a que este tipo de almacenamiento resulta especialmente adecuado para grandes volúmenes y determinadas cargas de escritura y consulta por claves o rangos.
**Column: 4/5.**
Graph no constituye el patrón principal de carga planteado por el escenario.
**Graph: 2/5.**
Object puede manejar grandes cantidades de información, pero no está orientado como almacén principal de eventos que requieren consultas estructuradas.
**Object: 3/5.**

### Consistencia requerida — 15%
Los eventos deben conservar los datos necesarios para poder ser almacenados y recuperados correctamente. El escenario también considera posibles fallos y la necesidad de definir qué sucede ante lecturas tardías o datos duplicados.
Las cuatro familias pueden proporcionar mecanismos para mantener distintos niveles de consistencia, pero sus características dependen de la tecnología y de la forma en que se diseñe la solución.
Por ello se asigna:

* **Document: 3/5**
* **Graph: 3/5**
* **Column: 3/5**
* **Object: 2/5**

Object recibe una valoración menor porque su modelo está orientado principalmente al almacenamiento de objetos y no a operaciones de datos con características transaccionales o consultas estructuradas.

### Costo operativo — 15%

El costo no se considera únicamente como el precio de almacenamiento. También se considera la complejidad de ejecutar, administrar, monitorear y mantener la tecnología.
Document puede ejecutarse localmente mediante Docker Compose y cuenta con alternativas equivalentes para entornos cloud.

**Document: 3/5.**
Graph puede requerir una tecnología especializada que no aporta una ventaja clara para el patrón de datos de M04.
**Graph: 2/5.**
Column puede ser conveniente para grandes volúmenes, aunque su infraestructura y operación deben justificarse por la carga real.
**Column: 3/5.**
Object puede presentar ventajas de costo para almacenamiento de archivos, histórico y blobs.
**Object: 4/5.**

### Simplicidad de integración — 15%

El modelo documental puede integrarse directamente con la aplicación mediante documentos JSON/BSON y un driver específico.
Para M04 se utilizó el driver oficial de MongoDB para Java, lo que permitió implementar el almacenamiento documental sin modificar el modelo relacional existente.

**Document: 4/5.**
Graph requiere modelar nodos y relaciones, aunque estas relaciones no son el elemento central del escenario.
**Graph: 2/5.**
Column requiere adaptar la estructura de los datos al modelo orientado a columnas.
**Column: 3/5.**
Object requiere una estrategia diferente para almacenar y posteriormente consultar información estructurada, por lo que no se ajusta directamente al patrón de eventos de M04.
**Object: 3/5.**

## Consideración de PostgreSQL

La decisión de utilizar MongoDB para M04 no significa que PostgreSQL deje de ser adecuado.
El análisis realizado anteriormente identificó que usuarios, cursos, inscripciones, calificaciones y progreso presentan relaciones claras y requieren consistencia. Por estas características, PostgreSQL continúa siendo adecuado como base principal.
Además, PostgreSQL dispone de JSONB para almacenar información semiestructurada.
Por lo tanto, la incorporación de MongoDB debe justificarse por las características específicas de los eventos: crecimiento, volumen, flexibilidad y carga de escritura, además de las necesidades de consulta y escalabilidad.
La arquitectura propuesta mantiene PostgreSQL para los datos relacionales y utiliza MongoDB de forma complementaria para el almacenamiento documental que justifique esta separación.

## Selección

La familia seleccionada para M04 es:
**Document**
La tecnología utilizada para implementar esta decisión es:
**MongoDB**
La selección se fundamenta en que el modelo documental proporciona un equilibrio adecuado para el patrón de eventos del escenario.
Los eventos pueden presentar información variable dependiendo del tipo de actividad, pueden crecer rápidamente y requieren consultas estructuradas por atributos como rango temporal, estado y origen.
MongoDB permite representar estos eventos como documentos y proporciona mecanismos para trabajar con grandes cantidades de información y escalar cuando sea necesario.
La selección también considera que la arquitectura general mantiene PostgreSQL para los datos relacionales y utiliza MongoDB únicamente donde sus características aporten una ventaja suficiente para justificar la complejidad adicional.

## Evidencia de implementación

La implementación de M04 utiliza MongoDB 7 mediante Docker Compose.
La conexión se configura mediante variables de entorno y la base utilizada para las pruebas es `cdrl_events`.
La integración con Java utiliza el driver oficial MongoDB Sync 5.2.1.
Las pruebas de `EventStoreTest` comprueban el comportamiento del almacenamiento documental mediante casos como:

* Flujo normal de inserción y consulta.
* Evento incompleto.
* Consulta de un rango sin resultados.
* Fallo ante una conexión inválida.

Estas pruebas permiten demostrar que la tecnología seleccionada puede ejecutar correctamente el comportamiento implementado para M04.
No obstante, estas pruebas representan **evidencia de implementación**, no constituyen por sí mismas la justificación de la decisión arquitectónica.

## Resultado de la verificación

La verificación realizada para M04 terminó con:

* **19 pruebas ejecutadas**
* **0 fallos**
* **0 errores**
* **0 pruebas omitidas**
* **BUILD SUCCESS**
* **M04 verification passed**

Estos resultados demuestran que la implementación realizada para MongoDB funciona correctamente en el entorno de pruebas.
La evidencia de los tests se utiliza para respaldar la implementación de la decisión y su reproducibilidad.

## Limitaciones

La matriz representa una decisión basada en el escenario y en los supuestos establecidos para M04.
Los puntajes no deben interpretarse como mediciones universales de las tecnologías. Un cambio significativo en el volumen, patrón de consultas, requisitos de consistencia, infraestructura o costos podría modificar la decisión.
Asimismo, la existencia de JSONB en PostgreSQL significa que MongoDB no es necesariamente indispensable para toda la plataforma.
La decisión de utilizar MongoDB debe mantenerse limitada al conjunto de datos donde sus características aporten suficiente beneficio frente al costo de administrar una segunda tecnología.
