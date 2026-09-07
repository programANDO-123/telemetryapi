# ADR-002 — Pruebas automáticas del contrato de telemetría

## Estado

Aceptado

## Contexto

Para validar el contrato de datos de telemetría se requieren pruebas
automáticas que comprueben tanto el comportamiento esperado de los datos
válidos como el rechazo de datos inválidos.

El contrato establece que los valores de `speed_kmh` y `distance_km` no
pueden ser negativos. Además, ambos campos son obligatorios y las lecturas
deben estar asociadas a un dispositivo existente.

Para las pruebas se utiliza PostgreSQL con los datos definidos en el seed
del proyecto y JUnit 5 mediante Maven.

## Datos considerados para las pruebas

Se utiliza el dispositivo `GPS-001` incluido en el seed de la base de datos.
Esto permite que las pruebas respeten la relación existente entre
`telemetry_readings` y `devices`.

Los valores seleccionados para las pruebas son:

| Prueba | speed_kmh | distance_km | Motivo |
|---|---:|---:|---|
| Caso normal | 45.50 | 7.250 | Representa una lectura válida con valores positivos. |
| Límite 1 | 0.00 | 1.000 | Comprueba que la velocidad mínima permitida sea cero. |
| Límite 2 | 1.00 | 0.000 | Comprueba que la distancia mínima permitida sea cero. |
| Fallo declarado | -0.01 | 1.000 | Comprueba que una velocidad negativa sea rechazada. |

## Justificación de los datos

### Caso normal

Se utilizan `45.50 km/h` de velocidad y `7.250 km` de distancia porque
ambos valores son positivos y cumplen las restricciones del contrato.

Esta prueba demuestra que una lectura válida puede insertarse correctamente
en la tabla `telemetry_readings`.

### Caso límite 1: velocidad igual a cero

Se utiliza `speed_kmh = 0.00`.

El contrato permite valores mayores o iguales a cero mediante la restricción:

`speed_kmh >= 0`

Por lo tanto, cero representa el límite inferior válido para la velocidad.

La prueba confirma que el sistema no rechace un valor que se encuentra
exactamente en el límite permitido.

### Caso límite 2: distancia igual a cero

Se utiliza `distance_km = 0.000`.

El contrato establece:

`distance_km >= 0`

Por lo tanto, cero representa el límite inferior válido para la distancia.

La prueba confirma que una lectura con distancia cero sea aceptada.

### Fallo declarado: velocidad negativa

Se utiliza `speed_kmh = -0.01`.

Este valor se encuentra fuera del contrato porque la velocidad no puede ser
negativa.

La prueba espera que PostgreSQL rechace la inserción mediante la restricción
`CHECK` correspondiente. Se valida específicamente el SQLSTATE `23514`,
que identifica una violación de una restricción `CHECK`.

Este caso se considera un fallo declarado porque el comportamiento esperado
es que la operación falle.

## Cobertura de las pruebas

Las pruebas automáticas cubren los siguientes escenarios:

- **1 caso normal:** inserción de una lectura válida.
- **2 casos límite:** velocidad igual a cero y distancia igual a cero.
- **1 fallo declarado:** rechazo de una velocidad negativa.

En conjunto, las pruebas verifican los límites inferiores definidos por el
contrato y comprueban que los datos que violan dichas restricciones sean
rechazados.

## Implementación

Las pruebas se implementan en:

`src/test/java/cdrl/TelemetryContractTest.java`

y se ejecutan automáticamente mediante Maven y JUnit 5:

```bash
mvn clean test