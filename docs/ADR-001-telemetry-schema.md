# ADR-001 — Contrato de datos de telemetría

## Estado

Aceptado por el equipo para M01.

## Contexto

Para M01 teniamos que dejar definido como se guardan las lecturas de telemetria en la base de datos. El tema que escogimos fue telemetria de autos pero en esta primera entrega solo vamos a guardar dos cosas por lectura: velocidad y distancia recorrida. No vamos a calcular metricas ni detectar eventos todavia, eso queda para despues.

Cada lectura sale de un dispositivo que ya tenemos dado de alta y trae la hora en que se tomó. La base debe rechazar datos que no tengan sentido, por ejemplo velocidad o distancia negativa, lecturas de dispositivos que no existen y lecturas del mismo dispositivo en el mismo instante.

## Decisión

Trabajamos con dos tablas en PostgreSQL: `devices` y `telemetry_readings`. Los nombres estan en ingles porque el codigo tambien.

### Tabla devices

Es como el catalogo de dispositivos.

- `device_id`: UUID y llave primaria. Usamos UUID en vez de un numero que va aumentando porque si algun dia hay mas entornos o alguien mas crea dispositivos, no se chocan los ids.
- `device_code`: codigo corto, obligatorio y unico. Es para reconocer el dispositivo facil, por ejemplo `GPS-001` se lee mejor que un UUID largo.

### Tabla telemetry_readings

Aqui va cada lectura que manda un dispositivo.

- `reading_id`: numero que se genera solo. Es la llave de la lectura, no del dispositivo.
- `device_id`: de que dispositivo viene la lectura. Es obligatorio y tiene que existir primero en devices. Asi no guardamos telemetria de dispositivos que no tenemos registrados.
- `recorded_at`: cuando se tomo la lectura. Lo guardamos con zona horaria para no perder el momento real.
- `speed_kmh`: velocidad en kilometros por hora. Obligatoria y no puede ser negativa.
- `distance_km`: distancia recorrida desde la lectura anterior en kilometros. Ojo, no es la distancia total del viaje, es solo lo que avanzo desde la ultima lectura. Tampoco puede ser negativa.

Un mismo dispositivo no puede tener dos lecturas en el mismo instante. Para eso hay una restriccion unica con `device_id` y `recorded_at` juntos.

La relacion es uno a muchos: un dispositivo puede tener muchas lecturas, pero cada lectura es de un solo dispositivo.

La migracion esta en `db/migrations/V1__create_telemetry_contract.sql`.

## Alternativas consideradas

- Meter todo en una sola tabla y repetir el codigo del dispositivo en cada lectura. Lo descartamos porque se repite informacion y es mas dificil asegurar que el dispositivo exista antes de guardar la lectura.
- Usar un id incremental para el dispositivo. Es valido sobre todo si solo tuvieramos una base, pero preferimos UUID por el tema de no depender de un contador.
- Agregar ubicacion, metricas calculadas o eventos. No van en esta entrega. Solo necesitamos el contrato de datos, la migracion, el seed y las pruebas. Lo demas se puede ir agregando despues.
- Usar TIMESTAMP sin zona horaria. Lo descartamos porque las lecturas pueden venir de distintos lugares y necesitamos ordenarlas bien.

## Consecuencias

- Si alguien intenta meter velocidad negativa, la misma base lo rechaza. No depende de que el codigo este revisando todo.
- No duplicamos la informacion del dispositivo.
- Se pueden agregar mas tablas despues sin tocar estas dos.
- Los NUMERIC tienen decimales fijos. Si despues ocupamos mas, hay que cambiar la tabla.

Limites por ahora:

- No guardamos ubicacion ni coordenadas.
- No calculamos promedios ni totales.
- No detectamos eventos como exceso de velocidad o frenadas.
- No pusimos limites maximos, solo que no sean negativos.
