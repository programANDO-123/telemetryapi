# ADR-002 — Lenguaje y stack del proyecto

## Estado

Aceptado por el equipo para M01.

## Contexto

En M01 el enunciado nos pide escoger el lenguaje de la aplicacion y dejarlo documentado. El proyecto es una API de telemetria de autos sobre PostgreSQL y las entregas van a ser semanales, asi que necesitabamos algo que arrancara rapido pero que tambien aguantara cuando el proyecto crezca.

En esta entrega todavia no hay API ni logica de negocio. Lo que hay es el contrato de datos, las pruebas y la base para seguir. Aun asi teniamos que decidir el lenguaje ahora, porque cambiarlo despues implicaria rehacer todo.

## Decisión

El stack es este:

- Java 21. Es la version LTS mas reciente y tiene cosas que nos sirven como records, pattern matching y virtual threads. Spring Boot 4 la soporta sin problemas.
- Maven para el build. El pom.xml hereda del parent de Spring Boot, lo que nos da versiones de dependencias alineadas sin que tengamos que fijarlas una por una.
- Spring Boot 4.1.1. Nos da servidor embebido (Tomcat), inyeccion de dependencias, configuracion por propiedades y un ecosistema enorme de starters. Es la version estable actual.
- PostgreSQL 16. Tiene UUID nativo, TIMESTAMPTZ, columnas IDENTITY, restricciones CHECK y UNIQUE, y ON CONFLICT para upserts. Todo eso lo usamos en el contrato.
- JUnit 5. Es lo que trae spring-boot-starter-test y es el estandar actual de testing en Java.
- Driver JDBC de PostgreSQL para las pruebas.
- Docker Compose para levantar PostgreSQL y DynamoDB en local sin instalarlos a mano.

Coordenadas Maven: com.telemetry / telemetry-api / 0.1.0-SNAPSHOT. Paquete base: com.telemetry.api.

Por ahora no metemos JPA, ni Flyway, ni Testcontainers. Cuando las siguientes entregas los pidan, entran.

## Alternativas consideradas

- Java 17 en vez de 21. La descartamos porque ya tenemos 21 en el entorno, es LTS y no hay motivo para quedarnos en una version anterior cuando el soporte a largo plazo es mejor en la nueva.
- Gradle en vez de Maven. Gradle es mas rapido en builds grandes y su DSL es mas flexible, pero Maven tiene un modelo mas declarativo, plugins mas estables y no requiere que cada integrante entienda un lenguaje de scripting distinto para tocar el build. Para un proyecto de este tamaño Maven es mas simple.
- Python o Node en vez de Java. Los dos arrancan mas rapido para prototipos, pero el proyecto va a tener reglas de negocio, varias capas y validaciones. Java con Spring Boot nos da esa estructura sin tener que armarla a mano.
- Spring Boot 3.x. Es la version anterior. La descartamos porque 4.1.1 es la estable mas reciente y ya trae Spring Framework 7 y Jakarta EE 11.
- Meter JPA desde ahora. No tiene sentido sin entidades ni repositorios. La capa de acceso la armamos cuando toque persistir algo mas que las pruebas.
- Una base en memoria para las pruebas (H2). No sirve porque dependemos de UUID nativo, TIMESTAMPTZ, columnas IDENTITY y ON CONFLICT. En H2 eso se comporta distinto o directamente no existe.
- Testcontainers desde ahora. Es buena herramienta, pero M01 ya tiene Docker Compose levantando la base y no necesitamos dos mecanismos para lo mismo. Entra cuando queramos que las pruebas levanten su propia base sin depender del compose.

## Consecuencias

- El stack es el estandar de Java backend, cualquier persona que se sume al equipo lo reconoce.
- Las pruebas corren contra PostgreSQL real. Lo que pasa en las pruebas es lo que va a pasar con la base de verdad.
- Todavia no hay endpoints. La app arranca y Tomcat queda escuchando, pero no responde nada porque no hay controladores.
- No usamos JPA ni Flyway por ahora. Entran cuando las siguientes entregas los pidan.
- Dependemos de Docker para levantar la base y para correr las pruebas. Sin Docker funcionando, make verify no corre.
- Maven centraliza las versiones en el pom.xml. Si agregamos una dependencia nueva, solo hay que declararla sin fijar version siempre que el parent la gestione.
