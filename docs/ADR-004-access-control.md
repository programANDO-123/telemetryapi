# ADR-004 — Control de acceso por roles

## Estado

Aceptado por el equipo para M03.

## Contexto

M03 requiere establecer un modelo de control de acceso por roles para separar las responsabilidades de migración, escritura, lectura y operación de la base de datos PostgreSQL.

El modelo debe aplicar el principio de mínimo privilegio, de manera que cada rol tenga únicamente los permisos necesarios para realizar las operaciones correspondientes. También se requiere demostrar mediante pruebas negativas que las operaciones no autorizadas son rechazadas por PostgreSQL.

Los roles definidos para M03 son:

* `migration_role`: responsable de operaciones relacionadas con migraciones y cambios de estructura.
* `writer_role`: responsable de operaciones de escritura sobre las tablas autorizadas.
* `reader_role`: responsable de operaciones de consulta.
* `operator_role`: responsable de operaciones de supervisión y monitoreo.

Las credenciales utilizadas durante la verificación son variables de entorno y no deben almacenarse como secretos versionados en el repositorio.

## Decisión

Se implementará control de acceso mediante roles nativos de PostgreSQL.

Se crearán los siguientes roles:

* `migration_role`
* `writer_role`
* `reader_role`
* `operator_role`

Los permisos se asignarán de acuerdo con la responsabilidad de cada rol:

* `migration_role` tendrá permisos de uso y creación sobre el esquema `public`, además de permisos completos sobre las tablas y secuencias existentes en dicho esquema para realizar operaciones de migración.
* `writer_role` tendrá permisos de uso sobre el esquema `public` y permisos `SELECT`, `INSERT` y `UPDATE` sobre `telemetry_readings` y `alerts`.
* `reader_role` tendrá permiso de uso sobre el esquema `public` y permisos de `SELECT` sobre las tablas del esquema.
* `operator_role` recibirá el rol `pg_monitor` para realizar tareas de supervisión y consulta relacionadas con la operación de PostgreSQL.

Las contraseñas de los roles se proporcionarán mediante variables de entorno:

* `MIGRATION_ROLE_PASSWORD`
* `WRITER_ROLE_PASSWORD`
* `READER_ROLE_PASSWORD`
* `OPERATOR_ROLE_PASSWORD`

Las credenciales sintéticas utilizadas en el entorno de desarrollo no se considerarán credenciales de producción y no deberán versionarse.

La validación del control de acceso se realizará mediante pruebas automatizadas en `AccessControlTest`, incluyendo casos normales, casos límite, un fallo declarado y tres pruebas negativas de acceso denegado.

La verificación también incluirá un análisis para detectar patrones de secretos versionados antes de ejecutar las pruebas.

## Alternativas consideradas

* Utilizar un único usuario con permisos completos para todas las operaciones. Se descartó porque no proporciona separación de responsabilidades ni mínimo privilegio.
* Utilizar permisos individuales directamente sobre usuarios sin roles separados. Se descartó porque dificulta la administración y reutilización de permisos.
* Mantener las credenciales directamente en los archivos versionados del proyecto. Se descartó porque expone secretos y contradice el requisito de mantenerlos fuera del repositorio.
* Utilizar roles separados en PostgreSQL. Se seleccionó porque permite aplicar permisos específicos a nivel de esquema, tabla y capacidades de monitoreo, además de permitir validar los accesos mediante pruebas automatizadas.

## Consecuencias

* Se obtiene una separación explícita entre migración, escritura, lectura y operación.
* Las operaciones no autorizadas pueden ser rechazadas por PostgreSQL mediante sus mecanismos nativos de privilegios.
* Las pruebas automatizadas permiten verificar que los permisos configurados corresponden al comportamiento esperado.
* Las credenciales se mantienen fuera del código versionado mediante variables de entorno.
* La administración de permisos requiere mantener sincronizadas las migraciones y las pruebas de control de acceso.
* Las contraseñas sintéticas utilizadas en desarrollo deben sustituirse y rotarse adecuadamente antes de utilizar un entorno real.
