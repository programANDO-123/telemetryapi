# Reporte M01 — Contrato de datos de telemetría

## Objetivo

Dejar armado el contrato de datos de telemetría para M01. Es decir, el esquema de la base, las migraciones, el seed, las pruebas y la evidencia del hito. Todo reproducible con los comandos make setup, make verify y make run.

## Alcance

Lo que sí entró en esta entrega:

- Esquema de dos tablas en PostgreSQL: devices y telemetry_readings.
- Migración idempotente en db/migrations/V1__create_telemetry_contract.sql.
- Seed sintético en db/seed/V2__seed_telemetry.sql con 3 dispositivos y 6 lecturas.
- Cuatro pruebas del contrato: un caso normal, dos casos límite y un fallo declarado.
- El proyecto base de Java 21 con Spring Boot 4.1.1.
- ADR-001 con el contrato de datos y ADR-002 con el stack.
- Artefacto de verificación en artifacts/m01-verify.json.

Lo que no entró:

- API HTTP ni endpoints. La app arranca pero no responde nada.
- Cálculo de métricas o detección de eventos.
- Despliegue en AWS. Por ahora todo corre en Docker local.
- JPA, Flyway ni Testcontainers. No los necesita M01.

## Entregables

| Que | Donde |
|-----|-------|
| Migración | db/migrations/V1__create_telemetry_contract.sql |
| Seed | db/seed/V2__seed_telemetry.sql |
| Pruebas | src/test/java/com/telemetry/api/TelemetryContractTest.java |
| App Spring Boot | src/main/java/com/telemetry/api/TelemetryApiApplication.java |
| ADR contrato | docs/ADR-001-telemetry-schema.md |
| ADR stack | docs/ADR-002-language-and-stack.md |
| Artefacto | artifacts/m01-verify.json |
| Evidencia | evidence/m01-data-contract.json |

## Decisiones

Las decisiones técnicas están documentadas en los dos ADRs. ADR-001 explica por qué dos tablas, por qué UUID en devices, por qué TIMESTAMPTZ en recorded_at y por qué una restricción única en device_id y recorded_at juntos. ADR-002 explica por qué Java 21, Maven, Spring Boot 4.1.1, PostgreSQL 16, JUnit 5 y Docker Compose.

## Pruebas

Las cuatro pruebas cubren lo siguiente:

- Caso normal: se inserta una lectura con velocidad 45.50 y distancia 7.250, se verifica que quedó bien guardada y se borra.
- Límite 1: velocidad 0.00. Es válido porque el CHECK permite mayor o igual a cero.
- Límite 2: distancia 0.000. Igual, es válido por el CHECK.
- Fallo declarado: velocidad negativa (-0.01). Aquí lo que se espera es que la base rechace el insert con un error de restricción CHECK. El test valida que el SQLSTATE sea 23514. O sea, el test pasa cuando la base hace lo correcto que es rechazar el dato.

Ese último caso es el que a veces confunde. No es un test que falla. Es un test que verifica que la base rechaza algo que debe rechazar.

## Evidencia

Al correr make verify, el script levanta PostgreSQL, aplica la migración y el seed, corre las pruebas y escribe el resultado en artifacts/m01-verify.json. El archivo de evidencia evidence/m01-data-contract.json apunta a ese resultado.

El artefacto queda así:

```json
{
  "command": "make verify",
  "status": "passed",
  "tests": "Tests run: 4, Failures: 0, Errors: 0, Skipped: 0"
}
```

## Límites conocidos

- No hay API. Todavía no exponemos endpoints.
- No hay métricas ni eventos.
- No hay despliegue en AWS.
- DynamoDB está declarado en el docker-compose pero no se levanta en make run, porque M01 no lo usa.
- Dependemos de Docker para correr make verify y make run.

## Conclusión

M01 deja el repositorio con un contrato de datos ejecutable y verificable. Los tres comandos públicos funcionan: make setup prepara el entorno, make verify corre las pruebas y genera el artefacto, make run levanta los contenedores y la app. El stack queda documentado y listo para las siguientes entregas.
