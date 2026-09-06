# ADR-001 — Esquema de telemetria M01

## Estado

Aceptado por el equipo para M01.

## Contexto

Para M01 necesitamos dejar definido como vamos a guardar los datos de telemetria en la base de datos.

El tema que escogimos es telemetria de autos, pero en esta primera entrega solo vamos a guardar velocidad y distancia recorrida por cada lectura. Por ahora no vamos a desarrollar el backend que haga calculos de metricas o que detecte eventos.

Lo primero que necesitamos es saber de que dispositivo viene cada lectura y en que momento se registro. Tambien necesitamos reglas basicas para que la base no permita datos que no tienen sentido, por ejemplo una velocidad o una distancia negativa.

## Decisión

Vamos a trabajar con dos tablas en PostgreSQL: `devices` y `telemetry_readings`.

Para el proyecto usamos nombres en ingles tanto para las tablas como para los campos, para mantener el mismo formato en el codigo.

### Tabla `devices`

Esta tabla guarda los datos necesarios para reconocer un dispositivo.

- `device_id`: UUID y clave primaria. Es el id interno del dispositivo. En lugar de usar un numero que vaya aumentando como 1, 2, 3..., usamos UUID para que el identificador no dependa de un contador de una sola base. Esto puede ayudar si despues hay mas de un entorno o lugares donde se creen dispositivos.

- `device_code`: codigo corto, obligatorio y unico. Sirve para reconocer facilmente el dispositivo. Por ejemplo, `GPS-001` es mas facil de leer que un UUID.

### Tabla `telemetry_readings`

Esta tabla guarda cada lectura que mande un dispositivo.

- `reading_id`: numero autogenerado. Es el identificador de la lectura. No identifica al dispositivo, solo a esa lectura.
- `device_id`: indica de que dispositivo viene la lectura. Es obligatorio y debe existir primero en la tabla `devices`. Asi evitamos guardar telemetria de un dispositivo que no tenemos registrado.
- `recorded_at`: indica la fecha y hora en que se tomo la lectura. Se guarda con zona horaria para guardar bien el momento en que ocurrio y poder ordenar las lecturas.
- `speed_kmh`: velocidad de la lectura en kilometros por hora. Es obligatoria y no puede tener un valor menor que 0.
- `distance_km`: distancia recorrida desde la lectura anterior, en kilometros. Es obligatoria y no puede ser negativa. No representa la distancia total del dispositivo, sino la distancia recorrida durante ese intervalo.
Un mismo dispositivo no puede tener dos lecturas registradas exactamente en el mismo momento. Por eso se usa la combinacion de `device_id` y `recorded_at` como dato unico.
- La relacion entre las tablas es de uno a muchos: un dispositivo puede tener muchas lecturas de telemetria, pero cada lectura pertenece a un solo dispositivo.
La primera migracion esta en `db/migrations/V1__create_telemetry_contract.sql`.

## Alternativas consideradas

- Usar una sola tabla y repetir el codigo del dispositivo en cada lectura: se descarto porque estariamos repitiendo informacion y seria mas dificil controlar que el dispositivo exista antes de guardar una lectura.
- Usar un ID incremental para el dispositivo: tambien es una opcion valida, sobre todo si solo tenemos una base de datos. Se prefirio UUID porque no depende de un contador y da mas margen si despues hay diferentes entornos o mas de una fuente creando dispositivos.
- Agregar ubicacion, metricas calculadas o eventos: no se incluyen ahora. En esta entrega solo se necesita el contrato de datos, las migraciones, el seed y las pruebas. La parte de calculos y eventos se puede trabajar despues.

## Límites conocidos

 - No se van a guardar coordenadas ni ubicacion en M01.
 - No se van a calcular promedios, totales ni otras metricas.
 - No se van a detectar eventos como exceso de velocidad, frenadas bruscas o aceleraciones. Por ahora solo vamos a controlar que velocidad y distancia no sean negativas. No se van a definir limites maximos ni alertas.

El seed y las pruebas son tareas separadas del equipo. Este ADR deja documentado solamente el contrato y la migracion inicial.
Elaboro: Jesus
