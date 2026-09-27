# ADR-005 — Decisión arquitectónica NoSQL

## Estado

Aceptado por el equipo para M04.

## Contexto

Cloud Data Reliability Lab (CDRL) recibe datos sintéticos que representan actividad operativa mediante eventos, señales, estados y fallos.
Para M04 se requiere seleccionar una alternativa NoSQL que pueda funcionar localmente y que pueda mapearse posteriormente a un servicio equivalente en la nube.
El patrón básico de evento considerado es:
```text
{ id, type, ts, deviceId }
```
Las consultas esperadas consideran información por rango, estado y origen.
La decisión debe considerar las restricciones de latencia, costo y consistencia, además de la escala, concurrencia y crecimiento de los datos.
El análisis realizado durante Semana 4 también considera que los eventos pueden presentar estructuras variables y que el escenario puede alcanzar aproximadamente 250,000 eventos diarios.
La decisión NoSQL debe realizarse comparando las familias Document, Graph, Column y Object.
No se selecciona una tecnología por popularidad o por tratarse de una tecnología conocida. La selección debe estar respaldada mediante una matriz ponderada, pruebas y artefactos reproducibles.

## Problema arquitectónico

El proyecto ya utiliza PostgreSQL para la información relacional.
Los datos como usuarios, cursos, inscripciones, calificaciones y progreso tienen relaciones claras y requieren consistencia. PostgreSQL también permite manejar información semiestructurada mediante JSONB.
Por ello, el problema no consiste en reemplazar PostgreSQL por una base NoSQL.
El problema consiste en determinar si existe un tipo de información dentro del escenario de CDRL cuyas características de volumen, crecimiento, flexibilidad, concurrencia y consultas justifiquen incorporar un almacenamiento NoSQL especializado.

## Opciones consideradas

### Document

El modelo documental representa la información mediante documentos JSON/BSON.
Es adecuado cuando el agregado puede vivir como documento y cuando la estructura puede cambiar.
Para CDRL permite representar eventos operativos con sus diferentes atributos sin requerir que las relaciones entre nodos sean el elemento principal del modelo.
### Graph

El modelo de grafos representa información mediante nodos y relaciones.
Es especialmente adecuado cuando las consultas dependen de recorridos, conexiones o caminos entre entidades.
El escenario de M04 no tiene como consulta principal el recorrido de relaciones profundas, por lo que esta característica no representa el principal requerimiento del problema.

### Column

El modelo de columnas anchas puede ser adecuado para grandes volúmenes y consultas por claves o rangos.
Esta alternativa es relevante para el escenario porque los eventos pueden crecer considerablemente y existir cargas importantes de escritura.
Sin embargo, debe analizarse si el patrón real de consultas y la flexibilidad requerida justifican utilizar este modelo frente a uno documental.

### Object

El almacenamiento de objetos está orientado principalmente a archivos, blobs, evidencias e información histórica de gran tamaño.
Aunque puede presentar ventajas de costo para almacenamiento masivo, no está diseñado como la opción principal para consultar eventos estructurados mediante atributos y rangos.

## Decisión

Se selecciona:
**Familia NoSQL: Document**
**Tecnología: MongoDB**
MongoDB se utilizará como almacenamiento NoSQL complementario para los eventos de actividad y telemetría contemplados en M04.
PostgreSQL permanece como la base principal para la información relacional del proyecto.

## Justificación de la decisión
La decisión se basa en cinco criterios principales:
1. **Consultas esperadas — 30%**
2. **Escala y escritura — 25%**
3. **Consistencia requerida — 15%**
4. **Costo operativo — 15%**
5. **Simplicidad de integración — 15%**
La matriz ponderada proporciona la siguiente comparación:

| Criterio                   |     Peso | Document |    Graph |   Column |   Object |
| -------------------------- | -------: | -------: | -------: | -------: | -------: |
| Consultas esperadas        |      30% |        4 |        2 |        3 |        1 |
| Escala y escritura         |      25% |        3 |        2 |        4 |        3 |
| Consistencia requerida     |      15% |        3 |        3 |        3 |        2 |
| Costo operativo            |      15% |        3 |        2 |        3 |        4 |
| Simplicidad de integración |      15% |        4 |        2 |        3 |        3 |
| **Total ponderado**        | **100%** | **3.40** | **2.20** | **3.35** | **2.55** |

La puntuación más alta corresponde a Document con **3.40**, seguida de Column con **3.35**.
La diferencia entre ambas alternativas es reducida, por lo que la selección de Document no se presenta como una superioridad universal de MongoDB sobre las demás tecnologías.
La selección responde específicamente al escenario de M04 y al equilibrio entre el patrón de consultas, la flexibilidad de los eventos, la integración y la escala esperada.

## Consideración de volumen y crecimiento

El escenario analizado en Semana 4 contempla aproximadamente:
**5,000 alumnos × 50 eventos diarios = 250,000 eventos diarios.**

Este volumen representa un crecimiento considerable de información.
Además, los eventos pueden contener información diferente dependiendo de la actividad que representan.
Por estas características, un modelo documental permite mantener una estructura flexible mientras los eventos aumentan.
La escala por sí sola no determina la selección, ya que Column también presenta características adecuadas para grandes volúmenes.
La selección de Document se realiza considerando conjuntamente el volumen, las consultas, la flexibilidad y la integración.
## Consideración de concurrencia y escritura
Los eventos representan actividad que puede generarse continuamente.
Esto implica una carga de escritura que puede crecer conforme aumenten los usuarios y las actividades de la plataforma.
La matriz considera este aspecto dentro del criterio **Escala y escritura**.
Document recibe una puntuación de 3 y Column una puntuación de 4 porque Column puede presentar ventajas específicas para determinados escenarios de alto volumen y escritura.
A pesar de ello, el patrón general de CDRL también requiere consultar diferentes atributos del evento y mantener flexibilidad en su estructura, por lo que Document resulta adecuado para el conjunto de requerimientos analizado.

## Consideración de consistencia

La consistencia es un criterio explícito de la decisión.
Los eventos deben conservar la información necesaria para ser almacenados y recuperados correctamente.
También deben considerarse situaciones como:
* Evento incompleto.
* Rango sin resultados.
* Datos duplicados.
* Lecturas tardías.
* Servicio no disponible.
La implementación de M04 incluye pruebas relacionadas con eventos incompletos, rangos vacíos y fallos de conexión.
Estas pruebas permiten verificar el comportamiento implementado, mientras que las reglas de consistencia forman parte del análisis arquitectónico.

## Consideración de costo y operación

La incorporación de MongoDB implica mantener una segunda tecnología además de PostgreSQL.
Esto aumenta:
* Infraestructura.
* Administración.
* Monitoreo.
* Mantenimiento.
* Respaldos.
* Conocimientos técnicos requeridos.
Por esta razón, MongoDB no se propone como sustituto completo de PostgreSQL.
La decisión consiste en utilizar MongoDB únicamente cuando los requerimientos de los eventos justifiquen asumir esta complejidad.
La implementación local utiliza Docker Compose, permitiendo reproducir el entorno de M04 sin depender de una cuenta personal de servicios cloud.
La misma decisión puede mapearse posteriormente a un servicio cloud equivalente.

## Consideración de PostgreSQL y JSONB

PostgreSQL continúa siendo una alternativa válida para información semiestructurada mediante JSONB.
Por lo tanto, no se considera que todo dato variable deba almacenarse automáticamente en MongoDB.
La arquitectura utiliza PostgreSQL para los datos relacionales y considera MongoDB para aquellos datos donde el volumen, crecimiento, flexibilidad y carga de eventos hagan conveniente separar el almacenamiento.
Si los requerimientos reales pudieran resolverse adecuadamente mediante PostgreSQL y JSONB sin afectar rendimiento, escalabilidad u operación, podría reconsiderarse la necesidad de mantener una segunda tecnología.

## Distribución propuesta

| Información     | Tecnología                         | Motivo                                                       |
| --------------- | ---------------------------------- | ------------------------------------------------------------ |
| Usuarios        | PostgreSQL                         | Relaciones con roles, cursos e inscripciones.                |
| Cursos          | PostgreSQL                         | Relaciones con docentes, alumnos y actividades.              |
| Inscripciones   | PostgreSQL                         | Relación entre alumnos y cursos.                             |
| Calificaciones  | PostgreSQL                         | Requieren consistencia y relaciones.                         |
| Progreso        | PostgreSQL                         | Relación con alumnos, cursos y actividades.                  |
| Eventos         | MongoDB                            | Crecimiento, volumen y posibilidad de estructuras variables. |
| Notificaciones  | PostgreSQL / MongoDB según el caso | Depende de la estructura y requerimientos reales.            |
| Configuraciones | MongoDB según el caso              | Puede ser conveniente cuando las propiedades sean variables. |

## Consecuencias positivas

* PostgreSQL continúa manejando las relaciones principales.
* Se mantiene la consistencia requerida para los datos relacionales.
* Los eventos pueden almacenarse mediante documentos flexibles.
* La solución permite separar cargas de trabajo diferentes.
* MongoDB puede escalar independientemente si el volumen de eventos aumenta.
* La decisión puede reproducirse localmente mediante Docker Compose.

## Consecuencias negativas

* Se administran dos tecnologías de bases de datos.
* Aumenta la complejidad operativa.
* Se requieren conocimientos de PostgreSQL y MongoDB.
* Aumentan las necesidades de monitoreo y respaldo.
* La distribución de información entre las bases debe diseñarse cuidadosamente.

## Riesgos

### Complejidad innecesaria

Si PostgreSQL y JSONB fueran suficientes para manejar los eventos, MongoDB podría representar una complejidad adicional sin beneficio suficiente.

### Consistencia entre sistemas

Si información relacionada se distribuye incorrectamente entre PostgreSQL y MongoDB, podrían generarse problemas de consistencia.

### Crecimiento

El crecimiento de eventos puede requerir posteriormente estrategias adicionales de índices, particionamiento, almacenamiento o distribución.

### Dependencia tecnológica

El uso de dos tecnologías incrementa el conocimiento técnico requerido para mantener la solución.

## Evidencia reproducible

La decisión debe quedar respaldada mediante evidencia que permita reproducir el análisis sobre el mismo commit.
El archivo esperado es:
```text
evidence/m04-nosql-decision.json
```
Debe contener información equivalente a:
```json
{
  "commit": "<sha>",
  "commands": [
    "make setup",
    "make verify"
  ],
  "selectedStore": "document",
  "results": {
    "tests": "passed"
  },
  "assumptions": [],
  "limitations": []
}
```

El archivo debe registrar el SHA final, comandos, resultados, supuestos, limitaciones y decisión seleccionada.
No debe contener:
* Tokens.
* Credenciales.
* Datos personales.
* Connection strings sensibles.
* Capturas como única evidencia.
* Valores secretos hardcodeados.

## Evidencia de pruebas

La implementación de M04 utiliza MongoDB 7 mediante Docker Compose y el driver oficial MongoDB Sync 5.2.1 para Java.
Las pruebas de `EventStoreTest` contemplan:
* Flujo normal de inserción y consulta.
* Evento incompleto.
* Consulta de rango vacío.
* Conexión inválida.
La verificación completa reportó:
* **19 pruebas ejecutadas**
* **0 fallos**
* **0 errores**
* **0 pruebas omitidas**
* **BUILD SUCCESS**
* **M04 verification passed**

Los tests demuestran que la implementación seleccionada funciona conforme a los casos definidos.
No obstante, los tests no constituyen por sí mismos la razón para seleccionar MongoDB. La razón corresponde al análisis arquitectónico documentado en este ADR y en la matriz ponderada.

## Limitaciones

La decisión se encuentra condicionada al escenario y a los supuestos establecidos para M04.
Los puntajes de la matriz no representan una comparación universal de las tecnologías NoSQL.
Un cambio en:
* volumen,
* concurrencia,
* consultas,
* consistencia,
* infraestructura,
* costos,
* o requisitos de escalabilidad

podría modificar la decisión.
Asimismo, PostgreSQL con JSONB sigue siendo una alternativa válida y debe reconsiderarse si los requerimientos futuros permiten resolver los eventos sin mantener una segunda tecnología.

## Conclusión

Para M04 se selecciona **MongoDB como tecnología de almacenamiento documental** dentro de una arquitectura donde **PostgreSQL continúa siendo la base principal**.
La decisión se fundamenta en el patrón de eventos de CDRL, sus consultas esperadas, el crecimiento de los datos, la flexibilidad de su estructura, la carga de escritura y las posibilidades de escalabilidad.
La matriz ponderada permite comparar Document, Graph, Column y Object de manera trazable, mientras que el ADR registra el contexto, las alternativas, la decisión, sus consecuencias, riesgos y limitaciones.
La implementación y los tests de M04 proporcionan evidencia reproducible de que la alternativa seleccionada puede ejecutarse correctamente.
La decisión no se basa en que MongoDB sea simplemente una tecnología NoSQL ni en que las pruebas hayan funcionado. Se basa en la relación entre los requerimientos del escenario y las características del modelo documental, considerando también el costo de introducir y mantener una segunda tecnología.
