# Procedimientos operativos

**Propósito:** cómo se hace, paso a paso, una tarea que necesita tocar la base de datos sin
saltarse las reglas del proyecto.

---

## Cargar las JRVs reales del CNE antes de una elección

La geografía de una elección (provincias, cantones, parroquias y JRVs) entra **por un changeset de
Liquibase versionado**, no por un script suelto ni por una pantalla de carga. Así la base de
producción y la de desarrollo tienen la misma geografía, y todo cambio queda en el historial del
repositorio.

> Las validaciones que tienen que cumplir los datos están en
> [`JRVS_IMPORT_GUIDE.md`](JRVS_IMPORT_GUIDE.md). Esta sección es el procedimiento; la guía es el
> contrato de los datos.

### 0. Qué hace falta

- El CSV del CNE con provincia, cantón, parroquia, JRV, recinto y electorate.
- El código de la provincia y de cada cantón (columna `codigo`), que es lo que se usa para
  encadenar los niveles.
- Java 25 y el módulo `backend/electoral-api-quarkus` (el changelog vive ahí: este módulo es su
  único dueño).

### 1. Descargar y leer los datos del CNE

El CNE publica el padrón de mesas y el listado de JRVs por provincia. Hay que dejar los datos en
cuatro columnas que se puedan mapear a la jerarquía:

```csv
provincia_codigo,provincia_nombre,canton_codigo,canton_nombre,parroquia_nombre,es_urbana,jrv_numero,recinto,total_electores
24,Santa Elena,2401,Santa Elena,Santa Elena,true,JRV-SE-001,Escuela Abdón Calderón,320
```

Antes de seguir, revisar contra la guía: códigos duplicados, `total_electores` en cero, recintos
vacíos y parroquias sin `es_urbana` explícito.

### 2. Crear el changeset

El archivo va en `backend/electoral-api-quarkus/src/main/resources/database/changelog/`, con el
siguiente número libre y un nombre que diga de qué elección es:

```
0NN-data-jrvs-<eleccion>-<YYYY>.sql
```

Ejemplo: `040-data-jrvs-2026-2026.sql`.

El changeset **debe** llevar el contexto `seed` (es dato de referencia, no esquema) y un rollback
explícito:

```sql
--liquibase formatted sql

-- changeset electoral:0NN-01-data-jrvs-<eleccion>-<YYYY> context:seed
-- Geografia de la eleccion <YYYY>: <N> JRVs en <M> parroquias de <K> cantones.
-- ...
-- rollback DELETE FROM jrv_cargo WHERE ...; DELETE FROM jrv WHERE ...; ...

-- === Provincia ===
INSERT INTO provincia (nombre, codigo)
SELECT 'Guayas', '09'
WHERE NOT EXISTS (SELECT 1 FROM provincia WHERE codigo = '09');
```

Después, añadir el `include` al master, **al final** de la lista:

```yaml
  - include: { file: database/changelog/040-data-jrvs-2026-2026.sql }
```

> ⚠️ **No editar un changeset ya aplicado.** Liquibase guarda el checksum de cada changeset: si
> el archivo cambia después de aplicado, la validación falla y la app no arranca
> (*"Validation Failed: 1 changesets check sum"*). Si hay que corregir algo, va en un changeset
> nuevo.

> ⚠️ **Dos trampas del parser de Liquibase** (formato SQL), que rompen el arranque sin decir por
> qué: un comentario que empieza con `-- changeset` se toma por una cabecera de changeset, y el
> rollback se toma desde la primera línea de comentario que empieza con `-- rollback` hasta el
> final del bloque. La guía lo explica con detalle.

### 3. Generar los INSERT

Cada nivel se resuelve por el código del padre, con su guarda `NOT EXISTS` para que el changeset
sea idempotente. Para un volumen grande de JRVs, lo más práctico es **generar el archivo con un
script** a partir del CSV, no escribirlo a mano.

La plantilla que hay que completar (los `INSERT` con `SELECT ... FROM padre WHERE codigo = ...`):

```sql
-- === Provincia ===
INSERT INTO provincia (nombre, codigo)
SELECT '«PROVINCIA_NOMBRE»', '«PROVINCIA_CODIGO»'
WHERE NOT EXISTS (SELECT 1 FROM provincia WHERE codigo = '«PROVINCIA_CODIGO»');

-- === Cantón ===
INSERT INTO canton (nombre, codigo, provincia_id)
SELECT '«CANTON_NOMBRE»', '«CANTON_CODIGO»', p.id
FROM provincia p
WHERE p.codigo = '«PROVINCIA_CODIGO»'
  AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '«CANTON_CODIGO»');

-- === Parroquia ===
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT '«PARROQUIA_NOMBRE»', «ES_URBANA», c.id
FROM canton c
WHERE c.codigo = '«CANTON_CODIGO»'
  AND NOT EXISTS (
    SELECT 1 FROM parroquia p WHERE p.nombre = '«PARROQUIA_NOMBRE»' AND p.canton_id = c.id);

-- === JRVs ===
INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT '«JRV_NUMERO»', '«RECINTO»', «TOTAL_ELECTORES», p.id
FROM parroquia p
WHERE p.nombre = '«PARROQUIA_NOMBRE»' AND p.es_urbana = «ES_URBANA»
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = '«JRV_NUMERO»');

-- === Cargos de la JRV ===
INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero IN ('«JRV_NUMERO_1»', '«JRV_NUMERO_2»')
  AND c.nombre IN ('Alcalde/sa', 'Concejal Urbano')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);
```

**El `NOT EXISTS` de la JRV es obligatorio**, no un adorno: `jrv.numero` no tiene índice único en
la base (lo garantiza la aplicación, con un 409), así que sin la guarda el mismo número puede
entrar dos veces.

### 4. Aplicar la migración

En el backend Quarkus la migración corre **al arrancar** (o en el initContainer del despliegue, que
es lo que hace el manifiesto de k8s). Para aplicarla a mano:

```bash
cd backend/electoral-api-quarkus
mvn liquibase:update \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/electoral_db \
  -Dliquibase.username=electoral_user \
  -Dliquibase.password=... \
  -Dliquibase.changeLogFile=src/main/resources/database/db.changelog-master.yaml
```

O, contra un entorno que se levanta con Docker Compose, se levanta el servicio de migración, que
hace exactamente eso con el dueño de la base:

```bash
docker compose --profile dev up db-migrate
```

### 5. Verificar

```sql
-- La jerarquía completa: cada JRV cuelga de una parroquia, de un cantón y de una provincia.
select pr.nombre as provincia, c.nombre as canton, p.nombre as parroquia,
       j.numero, j.recinto, j.total_electores
from jrv j
join parroquia p on p.id = j.parroquia_id
join canton c on c.id = p.canton_id
join provincia pr on pr.id = c.provincia_id
order by pr.codigo, c.codigo, j.numero;

-- Ni una JRV huerfana:
select count(*) from jrv j
left join parroquia p on p.id = j.parroquia_id
left join canton c on c.id = p.canton_id
left join provincia pr on pr.id = c.provincia_id
where pr.id is null;

-- Toda JRV operable (con al menos un cargo habilitado):
select count(*) from jrv j
where not exists (select 1 from jrv_cargo jc where jc.jrv_id = j.id and jc.activo);

-- Y ningun numero repetido:
select numero, count(*) from jrv group by numero having count(*) > 1;
```

Y en la aplicación: entrar a `/master/configuracion-geografica` y comprobar que el árbol muestra
las JRVs con su recinto y su número de electores.

### 6. Deshacer

Si el changeset se aplicó mal, se deshace el último:

```bash
mvn liquibase:rollback -Dliquibase.rollbackCount=1 \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/electoral_db \
  -Dliquibase.username=electoral_user \
  -Dliquibase.password=... \
  -Dliquibase.changeLogFile=src/main/resources/database/db.changelog-master.yaml
```

Esto ejecuta el `-- rollback` del changeset. Si no lo tiene, Liquibase **falla** (no inventa el
deshacer de un bloque de INSERTs) y la base se queda como estaba.

---

## Correcciones de datos en caliente

Para corregir un dato de la geografía que ya está en producción (un recinto mal escrito, un
electorado que se cambió), **no se edita la tabla**: se escribe un changeset nuevo con el
`UPDATE`, que es el que queda en el historial y el que se replica a los demás ambientes.

```sql
-- changeset electoral:0NN-02-correccion-recinto context:seed
UPDATE jrv SET recinto = 'Escuela Abdón Calderón (nombre correcto)'
WHERE numero = 'JRV-SE-001';
-- rollback UPDATE jrv SET recinto = 'Escuela Abdón Calderón' WHERE numero = 'JRV-SE-001';
```

---

## Cambios de schema

Un cambio de schema (columna nueva, índice nuevo) es un changeset **sin** contexto `seed`: es
esquema, y lo aplican todos los ambientes, incluido el que corre la suite de tests.

```sql
-- changeset electoral:0NN-03-columna-nueva
ALTER TABLE jrv ADD COLUMN direccion varchar(200);
-- rollback ALTER TABLE jrv DROP COLUMN direccion;
```

Los cambios de schema tienen que ir con el cambio de la entidad que los usa: si la columna se
agrega y la entidad no la mapea, la validación de Hibernate no lo nota; si la entidad la mapea y
la columna no existe, el arranque falla con *"Schema-validation: missing column"*.

---

**Documentos relacionados:** [`JRVS_IMPORT_GUIDE.md`](JRVS_IMPORT_GUIDE.md) (validaciones de los
datos), `odd/tasks/import-jrvs.md` (la ODD que originó este procedimiento).
