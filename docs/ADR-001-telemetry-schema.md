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

## seed reproducible

Se agregó el archivo `db/seed/V1__seed_telemetry.sql` con datos sintéticos para tres dispositivos de prueba:

- `GPS-001`
- `GPS-002`
- `GPS-003`

El seed contiene seis lecturas de telemetría con diferentes valores de velocidad y distancia.

Las inserciones utilizan `ON CONFLICT DO NOTHING` para evitar duplicados cuando el archivo se ejecuta más de una vez. Esto permite que el seed pueda ejecutarse de forma repetible en el entorno local.

El seed fue probado en PostgreSQL junto con la migración `V1__create_telemetry_contract.sql`.

docker compose exec -T postgres psql -U cdrl_dev -d cdrl -c "SELECT * FROM telemetry_readings ORDER BY device_id, recorded_at;"
 reading_id |              device_id               |      recorded_at       | speed_kmh | distance_km 
------------+--------------------------------------+------------------------+-----------+-------------
          1 | 11111111-1111-1111-1111-111111111111 | 2026-09-01 08:00:00+00 |      0.00 |       0.000
          2 | 11111111-1111-1111-1111-111111111111 | 2026-09-01 08:10:00+00 |     45.50 |       7.250
          3 | 11111111-1111-1111-1111-111111111111 | 2026-09-01 08:20:00+00 |     80.75 |      13.500
          4 | 22222222-2222-2222-2222-222222222222 | 2026-09-01 09:00:00+00 |     60.00 |      10.000
          5 | 22222222-2222-2222-2222-222222222222 | 2026-09-01 09:15:00+00 |      0.00 |       0.000
          6 | 33333333-3333-3333-3333-333333333333 | 2026-09-01 10:00:00+00 |    100.25 |      25.750
(6 rows)
Elaboro: Serafin

Pruebas automáticas del contrato de telemetría

## Estado

Aceptado

## Contexto

Para validar el contrato de datos de telemetría se requieren pruebas automáticas que comprueben tanto el comportamiento esperado de los datos válidos como el rechazo de datos inválidos.

El contrato establece que los valores de `speed_kmh` y `distance_km` no pueden ser negativos. Además, ambos campos son obligatorios y las lecturas deben estar asociadas a un dispositivo existente.

Para las pruebas se utiliza PostgreSQL con los datos definidos en el seed del proyecto y JUnit 5 mediante Maven.

## Datos considerados para las pruebas

Se utiliza el dispositivo `GPS-001` incluido en el seed de la base de datos. Esto permite que las pruebas respeten la relación existente entre `telemetry_readings` y `devices`.

Los valores seleccionados para las pruebas son:

| Prueba          | speed_kmh | distance_km | Motivo                                                |
| --------------- | --------: | ----------: | ----------------------------------------------------- |
| Caso normal     |     45.50 |       7.250 | Representa una lectura válida con valores positivos.  |
| Límite 1        |      0.00 |       1.000 | Comprueba que la velocidad mínima permitida sea cero. |
| Límite 2        |      1.00 |       0.000 | Comprueba que la distancia mínima permitida sea cero. |
| Fallo declarado |     -0.01 |       1.000 | Comprueba que una velocidad negativa sea rechazada.   |

## Justificación de los datos

### Caso normal

Se utilizan `45.50 km/h` de velocidad y `7.250 km` de distancia porque ambos valores son positivos y cumplen las restricciones del contrato.

Esta prueba demuestra que una lectura válida puede insertarse correctamente en la tabla `telemetry_readings`.

### Caso límite 1: velocidad igual a cero

Se utiliza `speed_kmh = 0.00`.

El contrato permite valores mayores o iguales a cero mediante la restricción:

`speed_kmh >= 0`

Por lo tanto, cero representa el límite inferior válido para la velocidad.

La prueba confirma que el sistema no rechace un valor que se encuentra exactamente en el límite permitido.

### Caso límite 2: distancia igual a cero

Se utiliza `distance_km = 0.000`.

El contrato establece:

`distance_km >= 0`

Por lo tanto, cero representa el límite inferior válido para la distancia.

La prueba confirma que una lectura con distancia cero sea aceptada.

### Fallo declarado: velocidad negativa

Se utiliza `speed_kmh = -0.01`.

Este valor se encuentra fuera del contrato porque la velocidad no puede ser negativa.

La prueba espera que PostgreSQL rechace la inserción mediante la restricción `CHECK` correspondiente. Se valida específicamente el SQLSTATE `23514`, que identifica una violación de una restricción `CHECK`.

Este caso se considera un fallo declarado porque el comportamiento esperado es que la operación falle. La prueba se considera exitosa cuando PostgreSQL rechaza correctamente el dato inválido.

## Cobertura de las pruebas

Las pruebas automáticas cubren los siguientes escenarios:

* **1 caso normal:** inserción de una lectura válida.
* **2 casos límite:** velocidad igual a cero y distancia igual a cero.
* **1 fallo declarado:** rechazo de una velocidad negativa.

En conjunto, las pruebas verifican los límites inferiores definidos por el contrato y comprueban que los datos que violan dichas restricciones sean rechazados.

## Implementación

Las pruebas se implementan en:

`src/test/java/cdrl/TelemetryContractTest.java`

Se utiliza:

* Java 21.
* JUnit 5.
* Maven.
* PostgreSQL.
* JDBC para la conexión con la base de datos.

Las pruebas utilizan el dispositivo `GPS-001` proporcionado por el seed.

Las lecturas válidas utilizadas durante las pruebas se eliminan después de cada caso para evitar modificar permanentemente los datos del seed.

El caso de fallo declarado verifica que PostgreSQL produzca el SQLSTATE `23514` al intentar insertar una velocidad negativa.

## Ejecución

Las pruebas pueden ejecutarse directamente mediante Maven:

```bash
mvn clean test
```

También se ejecutan como parte del proceso de verificación del proyecto mediante:

```bash
make verify
```

Durante `make verify`, se verifica la base de datos y posteriormente se ejecutan las pruebas Java mediante Maven.

## Resultado de la verificación

La ejecución de `make verify` fue validada correctamente con el siguiente resultado:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
CDRL starter base verification passed
```

Esto confirma que las cuatro pruebas automáticas fueron encontradas y ejecutadas correctamente:

* 1 caso normal.
* 2 casos límite.
* 1 fallo declarado.

El fallo declarado no representa un error de la prueba. La prueba espera que la base de datos rechace el valor negativo y considera correcto que se produzca la violación de la restricción `CHECK`.

## Decisión

Se adopta JUnit 5 mediante Maven como mecanismo para las pruebas automáticas del contrato de telemetría.

Las pruebas utilizan los datos del seed para garantizar que el dispositivo empleado en las pruebas exista en la base de datos.

La ejecución mediante `make verify` integra las pruebas automáticas al proceso de verificación del proyecto.

El contrato validado mantiene las restricciones:

* `speed_kmh >= 0`
* `distance_km >= 0`

Las pruebas permiten comprobar automáticamente que los valores válidos sean aceptados y que los valores que violan estas restricciones sean rechazados.
