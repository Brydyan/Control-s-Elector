# Guía de importación de JRVs (datos del CNE)

**Propósito:** esta guía dice qué tiene que cumplir un archivo de geografía electoral (provincias,
cantones, parroquias y JRVs) antes de entrar a la base, y cómo se mete sin romper nada.

**Contexto:** la geografía de una elección no se edita a mano en la base: entra por un changeset
de Liquibase, versionado, que se aplica con el resto del schema. Así la base de producción y la
de desarrollo tienen la misma geografía, y todo cambio queda en el historial del repositorio.

---

## 1. El modelo de datos

Cuatro niveles, y cada nivel apunta al de arriba:

```
provincia (codigo UNIQUE, 10)
   └── canton (codigo UNIQUE, 10, provincia_id → provincia)
         └── parroquia (nombre, es_urbana, canton_id → canton)
               └── jrv (numero UNIQUE, recinto, total_electores, parroquia_id → parroquia)
```

Los índices que aplican las reglas de unicidad están en la base:

| Tabla | Índice | Qué garantiza |
|---|---|---|
| `provincia` | `ix_provincia_codigo` UNIQUE | un código por provincia |
| `canton` | `ix_canton_codigo` UNIQUE | un código de cantón en **todo el sistema** |
| `jrv` | **ninguno sobre `numero`** | el número de JRV **no** es único en la base: lo garantiza la aplicación (409) |

> ⚠️ **El número de JRV no tiene índice único.** `jrv.numero` es `NOT NULL`, pero nada impide que
> dos JRVs tengan el mismo número: la unicidad la comprueba la aplicación al crear o editar la JRV
> y responde 409 *"Ya existe una JRV con ese numero"*. Si la importación se hace por SQL directo
> (un changeset), esa comprobación **no se ejecuta**: el chequeo hay que hacerlo en el propio
> changeset, con la guarda `NOT EXISTS`, que es justo lo que impide el duplicado.

> ⚠️ **El código de cantón es global, no por provincia.** Un mismo código no puede repetirse en
> dos provincias, aunque el manual del CNE lo presente como "código de cantón dentro de la
> provincia". El código de provincia va dentro del código de cantón (`24` → `2401`, `2402`), y por
> eso el índice global no da problemas con los datos reales: el prefijo hace el código único.

---

## 2. Validaciones obligatorias

Antes de generar el changeset, estos seis chequeos tienen que pasar. Los tres primeros los
impone la base (y si no pasan, el INSERT revienta con una violación de índice o de clave foránea);
los tres últimos los impone la aplicación (y si no pasan, el `POST` responde 400).

### 2.1 Códigos únicos

- `provincia.codigo`: único en la tabla, hasta 10 caracteres.
- `canton.codigo`: único en **toda** la tabla, hasta 10 caracteres.
- `jrv.numero`: único en toda la tabla (lo exige la aplicación; la base no lo impide), hasta 20
  caracteres.

En Postgres un índice UNIQUE sobre una columna que permite NULL admite **varios NULL**: por eso
`provincia.codigo` y `canton.codigo` son opcionales, pero si vienen, no pueden repetirse.

### 2.2 Integridad referencial

- `canton.provincia_id` → tiene que existir una provincia.
- `parroquia.canton_id` → tiene que existir un cantón.
- `jrv.parroquia_id` → tiene que existir una parroquia.

En el changeset esto se resuelve por el **código del padre**, no por el id: los ids dependen del
orden de inserción y los datos del CNE no los traen.

```sql
-- correcto: el padre se busca por su código
INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Guayaquil', '0901', p.id
FROM provincia p
WHERE p.codigo = '09';

-- MAL: un id fijo solo funciona en una base donde ese id sea ese provincia
INSERT INTO canton (nombre, codigo, provincia_id)
VALUES ('Guayaquil', '0901', 7);
```

### 2.3 `total_electores > 0`

La columna es `NOT NULL` y la aplicación valida que sea mayor que cero (`@Min(1)`). Una JRV con
cero electores es una JRV que no puede tener mesa: es un error de transcripción del CSV.

### 2.4 `recinto` no vacío

`NOT NULL`, hasta 200 caracteres. La aplicación exige que no sea solo espacios. Un recinto es
el centro de votación: sin él, el observer no sabe dónde está la JRV.

### 2.5 `es_urbana` explícito en la parroquia

La parroquia urbana y la rural se distinguen con `es_urbana`. Un `NULL` no es un valor válido
para el modelo (la columna es `NOT NULL`): cada parroquia se declara urbana o rural.

### 2.6 La JRV tiene cargos habilitados

Una JRV sin filas en `jrv_cargo` existe pero no es operable: el observer no puede registrar un
acta para un par (JRV, cargo) que la JRV no tenga habilitado, y el backend responde 409.

```sql
INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero IN ('JRV-GY-001', 'JRV-GY-002')
  AND c.nombre IN ('Alcalde/sa', 'Concejal Urbano')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);
```

---

## 3. Ejemplos

### 3.1 Datos válidos

```sql
-- Provincia
INSERT INTO provincia (nombre, codigo)
SELECT 'Guayas', '09'
WHERE NOT EXISTS (SELECT 1 FROM provincia WHERE codigo = '09');

-- Cantón
INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Guayaquil', '0901', p.id
FROM provincia p
WHERE p.codigo = '09'
  AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '0901');

-- Parroquia
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Carapungo', TRUE, c.id
FROM canton c
WHERE c.codigo = '0901'
  AND NOT EXISTS (
    SELECT 1 FROM parroquia p WHERE p.nombre = 'Carapungo' AND p.canton_id = c.id);

-- JRV
INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-GY-001', 'Unidad Educativa César Chávez', 480, p.id
FROM parroquia p
WHERE p.nombre = 'Carapungo' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-GY-001');
```

### 3.2 Datos inválidos y por qué

| Dato | Qué pasa |
|---|---|
| `INSERT INTO jrv (numero, ...)` con `numero` repetido | **La base lo acepta**: no hay índice único. El duplicado se cuela y después la aplicación rechaza la JRV con 409. Por eso la guarda `NOT EXISTS` del changeset es obligatoria |
| `INSERT INTO canton (codigo = '2401')` cuando '2401' ya existe en otra provincia | Violación de `ix_canton_codigo`: el código de cantón es global |
| `INSERT INTO jrv (..., parroquia_id = 999)` | Violación de clave foránea `fk_jrv_parroquia` |
| `INSERT INTO jrv (..., total_electores = 0)` | La base lo acepta; la **aplicación** lo rechaza con 400 (`totalElectores` tiene que ser ≥ 1) |
| `INSERT INTO jrv (..., recinto = '   ')` | La base lo acepta; la **aplicación** lo rechaza con 400 (el recinto no puede ser solo espacios) |
| `INSERT INTO parroquia (nombre, es_urbana)` sin `es_urbana` | Violación de `NOT NULL` |
| `INSERT INTO jrv (..., recinto = NULL)` | Violación de `NOT NULL` |

### 3.3 Cómo se ve un dato inválido en la aplicación

La pantalla de configuración geográfica (`/master/configuracion-geografica`) es la forma rápida de
comprobar que un dato entró bien: si la JRV aparece en el árbol, con su recinto y su número de
electores, la fila pasó las validaciones. Si el backend la rechazó, el motivo está en el mensaje
del 400/409.

---

## 4. Idempotencia

Todo INSERT del changeset lleva su guarda `NOT EXISTS`, y esa guarda es **por clave de negocio**,
no por id:

| Tabla | La guarda mira |
|---|---|
| `provincia` | `codigo` |
| `canton` | `codigo` |
| `parroquia` | `nombre` **y** `canton_id` (el mismo nombre puede existir en dos cantones) |
| `jrv` | `numero` |
| `jrv_cargo` | `jrv_id` **y** `cargo_id` |

Con eso, correr el changeset dos veces no duplica nada: la segunda corrida inserta 0 filas. Es
lo que permite que una migración se reintente sin miedo y que el rollback se pueda rehacer.

---

## 5. Cómo deshacer

Un changeset de INSERTs planos **no** tiene rollback automático: Liquibase solo lo inventa para
DDL. Sin un `-- rollback` explícito, `rollback-count 1` falla y la base queda con la geografía a
medias.

Por eso el changeset declara el suyo, respetando el orden de las claves foráneas:

```sql
-- rollback DELETE FROM jrv_cargo WHERE jrv_id IN (...); DELETE FROM jrv WHERE numero IN (...); DELETE FROM parroquia WHERE ...; DELETE FROM canton WHERE ...; DELETE FROM provincia WHERE ...;
```

Para deshacer el último changeset aplicado:

```bash
mvn liquibase:rollback -Dliquibase.rollbackCount=1 \
  -Dliquibase.url=jdbc:postgresql://localhost:5432/electoral_db \
  -Dliquibase.username=electoral_user \
  -Dliquibase.password=... \
  -Dliquibase.changeLogFile=src/main/resources/database/db.changelog-master.yaml
```

> En el backend Quarkus la migración corre al arrancar (o en el initContainer del despliegue), no
> con el plugin de Maven. Para el procedimiento completo de una elección, ver
> `documents/OPERATIONS.md`.

---

## 6. Trampas del parser de Liquibase (formato SQL)

Dos cosas rompen el arranque y ninguna dice qué pasa:

1. **Un comentario que empieza con `-- changeset` se toma por una cabecera de changeset.** Si el
   texto de un comentario empieza con esa palabra, el parser lo interpreta como un cambio nuevo y
   falla con *"Unexpected formatting in formatted changelog"*.
2. **El rollback se toma desde la primera línea de comentario que empieza con `-- rollback`** hasta
   el final del bloque de comentarios. Si un comentario explicativo empieza con esa palabra, el
   rollback se traga el texto del comentario y la base dice *"syntax error"*.

Regla práctica: en el bloque de comentarios de un changeset, ninguna línea empieza con
`-- changeset` ni con `-- rollback`, salvo la última, que es el rollback de verdad.

---

**Documentos relacionados:** `documents/OPERATIONS.md` (procedimiento de una elección),
`odd/tasks/import-jrvs.md` (la ODD que originó esta guía).
