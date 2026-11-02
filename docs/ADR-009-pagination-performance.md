# ADR-009 — Paginación y rendimiento

## Estado

Aceptado por el equipo para M08.

## Contexto

El recorrido de eventos requiere paginación para limitar la cantidad de registros devueltos por consulta. La paginación por offset permite acceder a una página por número, pero debe omitir los registros anteriores al desplazamiento solicitado. Además, una inserción anterior a la posición consultada puede desplazar los resultados y provocar duplicados entre páginas.

Para M08 se compararon offset y cursor en Docker Compose local, con 10 000 documentos, páginas de 100 registros y 20 iteraciones por estrategia. Offset obtuvo p50 de 12 ms y p95 de 27 ms; cursor obtuvo p50 de 3 ms y p95 de 5 ms. Las mediciones corresponden a consultas directas a MongoDB.

## Decisión

Utilizar paginación por cursor para recorrer eventos secuencialmente. Los eventos se ordenan por `timestamp` ascendente y cada página siguiente consulta registros con `timestamp` estrictamente mayor al del último evento recibido. El tamaño de página se limita mediante `Pageable`.

Mantener la paginación por offset como línea base para comparar rendimiento y demostrar su comportamiento ante inserciones.

Se acepta el trade-off de priorizar la estabilidad del punto de continuación y la menor latencia observada frente al salto directo a una página por número. El cursor requiere conocer el punto de continuación para acceder a una página posterior.

## Alternativas consideradas

- **Offset:** facilita el salto directo por número de página, pero las inserciones anteriores al desplazamiento pueden repetir registros entre páginas. En el benchmark presentó mayor latencia.
- **Cursor por timestamp:** permite continuar desde el último valor recibido y obtuvo menor latencia en el escenario medido. Es la alternativa elegida para M08, con los límites indicados a continuación.
- **Cursor compuesto por timestamp e identificador único:** permitiría resolver los empates de timestamp mediante un orden total. Es una posible mejora; no forma parte de la implementación aportada.

## Consecuencias

- En la muestra local, cursor redujo la latencia p50 un 75 % y la p95 aproximadamente un 81,5 % respecto de offset.
- La navegación pasa a ser secuencial: no se dispone de salto directo a una página arbitraria por número sin contar con su cursor.
- Las inserciones anteriores al cursor no desplazan el punto de continuación, pero tampoco se recuperan al continuar ese recorrido. Las posteriores pueden aparecer en páginas siguientes.
- El cursor no representa una instantánea del conjunto de datos ni garantiza recuperar todos los cambios concurrentes.
- Si varios eventos comparten timestamp y una página termina dentro de ese grupo, la condición `timestamp > cursor` puede omitir los restantes. Para garantizar continuidad con empates se requiere un criterio adicional de desempate.
- Al consultar timestamps como cadenas, se requiere una representación normalizada que preserve el orden cronológico.
- Los resultados se limitan a 10 000 documentos y 20 iteraciones por estrategia en un entorno local. No constituyen una garantía de rendimiento en producción.
- El log muestra índices aplicados, pero no incluye planes de ejecución que permitan confirmar cuáles utilizaron las consultas.
