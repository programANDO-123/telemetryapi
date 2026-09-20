# Reporte M03 — Control de acceso por roles

## Objetivo

Implementar y verificar un modelo de control de acceso por roles en PostgreSQL, separando las responsabilidades de migración, escritura, lectura y operación.

El objetivo también incluye demostrar mediante pruebas automatizadas que los roles pueden realizar las operaciones permitidas y que las operaciones no autorizadas son rechazadas, manteniendo las credenciales fuera de los archivos versionados.

## Alcance

El alcance de M03 comprende:

* Creación y configuración de cuatro roles de PostgreSQL.
* Asignación de permisos diferenciados según la responsabilidad de cada rol.
* Configuración de credenciales mediante variables de entorno.
* Verificación de ausencia de secretos versionados.
* Pruebas de operaciones permitidas.
* Pruebas de operaciones no autorizadas.
* Validación de errores de autorización mediante SQLSTATE `42501`.
* Generación de evidencia mediante `artifacts/m03-verify.json`.

La implementación se realiza sobre PostgreSQL utilizado por el proyecto Telemetry API.

## Entregables

Los principales entregables de M03 son:

* `docs/ADR-004-access-control.md`
* `docs/M03-report.md`
* `db/migrations/V5__access_control.sql`
* `src/test/java/com/telemetry/api/AccessControlTest.java`
* `scripts/check_no_secrets.sh`
* `scripts/verify_m03.sh`
* `artifacts/m03-verify.json`

La verificación se ejecuta mediante:

```bash
make setup
make verify
make run
```

## Roles y permisos

### `migration_role`

Se utiliza para operaciones relacionadas con la estructura y migraciones de la base de datos.

Permisos configurados:

* `USAGE` sobre el esquema `public`.
* `CREATE` sobre el esquema `public`.
* `ALL` sobre las tablas existentes del esquema `public`.
* `ALL` sobre las secuencias existentes del esquema `public`.

### `writer_role`

Se utiliza para operaciones de escritura sobre las tablas autorizadas.

Permisos configurados:

* `USAGE` sobre el esquema `public`.
* `SELECT`, `INSERT` y `UPDATE` sobre `telemetry_readings`.
* `SELECT`, `INSERT` y `UPDATE` sobre `alerts`.
* `USAGE` y `SELECT` sobre las secuencias existentes del esquema `public`.

No se le concede permiso general de creación de tablas, por lo que las operaciones DDL no autorizadas deben ser rechazadas.

### `reader_role`

Se utiliza para operaciones de consulta.

Permisos configurados:

* `USAGE` sobre el esquema `public`.
* `SELECT` sobre las tablas del esquema `public`.

No se le conceden permisos de inserción.

### `operator_role`

Se utiliza para tareas de operación y supervisión.

Se le concede el rol:

```text
pg_monitor
```

Esto permite utilizar las capacidades de monitoreo proporcionadas por PostgreSQL sin asignarle permisos generales de escritura sobre las tablas de negocio.

## Pruebas

La ejecución de:

```bash
make verify
```

levantó PostgreSQL, aplicó las migraciones y ejecutó las pruebas automatizadas.

Resultado obtenido:

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
M03 verification passed
```

Las pruebas se distribuyen de la siguiente manera:

### Caso normal

Se verifica que cada rol pueda realizar una operación correspondiente a su responsabilidad:

* `migration_role` puede realizar una operación DDL.
* `writer_role` puede insertar información en `telemetry_readings`.
* `reader_role` puede consultar información de `vehicles`.
* `operator_role` puede consultar información de monitoreo mediante `pg_stat_activity`.

### Casos límite

Se comprueba el comportamiento de los permisos de `writer_role` y `reader_role`.

Entre las operaciones verificadas se encuentran:

* Actualización sobre `telemetry_readings`.
* Operaciones autorizadas sobre `alerts`.
* Consulta de información mediante `reader_role`.

### Fallo declarado

Se intenta realizar una operación de inserción sobre `alerts` utilizando `operator_role`.

La operación debe ser rechazada por PostgreSQL y la prueba valida específicamente el código:

```text
SQLSTATE 42501
```

### Pruebas negativas

Se incluyen tres pruebas de acceso denegado:

1. `writer_role` intenta ejecutar DDL mediante la creación de una tabla.
2. `reader_role` intenta insertar información en `alerts`.
3. `migration_role` intenta consultar el catálogo de roles mediante `pg_authid`.

Las operaciones no autorizadas se validan mediante el SQLSTATE `42501`.

El resultado global fue de:

```text
15 pruebas ejecutadas
0 fallos
0 errores
0 pruebas omitidas
```
## Rotación de secretos

Las credenciales de los cuatro roles se administran mediante variables de entorno y no se almacenan en archivos versionados.

Las variables utilizadas son:

```text
MIGRATION_ROLE_PASSWORD
WRITER_ROLE_PASSWORD
READER_ROLE_PASSWORD
OPERATOR_ROLE_PASSWORD
```

### Procedimiento de rotación

Para rotar una credencial se seguirá el siguiente procedimiento:

**1. Generar una nueva contraseña**

La nueva contraseña debe generarse de forma segura y mantenerse fuera del repositorio.

**2. Actualizar la variable de entorno**

Se sustituye el valor anterior de la variable correspondiente en el entorno donde se ejecuta PostgreSQL y las pruebas.

Por ejemplo:

```text
WRITER_ROLE_PASSWORD=<nueva-contraseña>
```

No se debe colocar el valor real de la contraseña en documentación, código fuente, commits o archivos rastreados por Git.

**3. Cambiar la contraseña en PostgreSQL**

Se conecta con una cuenta administrativa y se ejecuta `ALTER ROLE` para asignar la nueva contraseña al rol correspondiente.

Ejemplo conceptual:

```sql
ALTER ROLE writer_role WITH PASSWORD '<nueva-contraseña>';
```

El mismo procedimiento se aplica a:

```text
migration_role
reader_role
operator_role
```

**4. Actualizar los consumidores**

Los procesos, scripts o pruebas que utilicen el rol deben recibir la nueva variable de entorno. Esto permite que las conexiones posteriores utilicen la nueva credencial.

**5. Validar la nueva credencial**

Se ejecuta:

```bash
make verify
```

La verificación debe confirmar que los roles pueden realizar únicamente las operaciones declaradas y que las pruebas negativas continúan rechazando las operaciones no autorizadas.

**6. Comprobar que no existen secretos versionados**

Se ejecuta:

```bash
bash scripts/check_no_secrets.sh
```

El resultado esperado es:

```text
No versioned secrets detected
```

**7. Invalidar la credencial anterior**

Una vez confirmada la nueva credencial, la contraseña anterior deja de ser válida al haber sido reemplazada mediante `ALTER ROLE`.

### Rotación ante exposición

Si una credencial se expone accidentalmente:

1. Rotar inmediatamente la contraseña afectada.
2. Actualizar las variables de entorno de los servicios dependientes.
3. Ejecutar `make verify`.
4. Ejecutar `bash scripts/check_no_secrets.sh`.
5. Revisar el historial del repositorio para determinar el alcance de la exposición.
6. Si el secreto fue versionado, retirarlo del repositorio siguiendo el procedimiento de limpieza de secretos correspondiente.
7. Generar una nueva credencial y evitar reutilizar la anterior.

Las contraseñas sintéticas utilizadas durante el desarrollo de M03 no deben utilizarse en producción.
## Evidencia

La verificación se realizó mediante:

```bash
make verify
```

El resultado registrado fue:

```json
{
  "command": "make verify",
  "status": "passed",
  "tests": "Tests run: 15, Failures: 0, Errors: 0, Skipped: 0"
}
```

Durante la ejecución también se obtuvo:

```text
No versioned secrets detected
```

La verificación confirmó además que PostgreSQL se encontraba disponible, que las migraciones y permisos pudieron aplicarse y que las pruebas `AccessControlTest`, `RelationalModelTest` y `TelemetryContractTest` finalizaron correctamente.

Resultados por conjunto:

* `AccessControlTest`: 7 pruebas, 0 fallos.
* `RelationalModelTest`: 4 pruebas, 0 fallos.
* `TelemetryContractTest`: 4 pruebas, 0 fallos.

Total:

```text
15 pruebas
15 exitosas
0 fallos
0 errores
```

La evidencia se genera en:

```text
artifacts/m03-verify.json
```

## Límites conocidos

* Las contraseñas mostradas en el entorno de desarrollo son sintéticas y no representan una configuración de producción.
* La rotación depende de actualizar las variables de entorno y las credenciales correspondientes en PostgreSQL.
* Los permisos configurados corresponden al alcance definido para M03 y no constituyen por sí mismos un modelo completo de seguridad para un entorno productivo.
* La prueba automatizada valida los permisos definidos en el entorno de PostgreSQL utilizado durante `make verify`; cambios posteriores en roles o privilegios requieren volver a ejecutar las pruebas.
* La detección de secretos se basa en los patrones definidos por `scripts/check_no_secrets.sh`, por lo que no sustituye una revisión completa de gestión de secretos.

## Conclusión

M03 implementó un modelo de control de acceso basado en roles separados para migración, escritura, lectura y operación.

La configuración de PostgreSQL fue validada mediante pruebas automatizadas y pruebas negativas de acceso. La ejecución de `make verify` finalizó correctamente con **15 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas**.

Además, la verificación confirmó:

```text
No versioned secrets detected
```

Con estos resultados, la implementación cumple con la validación definida para M03 en el entorno de desarrollo, manteniendo las credenciales fuera de los archivos versionados y comprobando mediante pruebas que las operaciones no autorizadas son rechazadas por PostgreSQL.
