# Design — Reparar el orden de migraciones RBAC y la compuerta de CI

---

## D1 — El seed de `rol_permiso` se mueve después de la creación de `rol`, no al revés

**Decisión**: el changeset que siembra `rol_permiso` deja de vivir en `002-rbac-seed.sql` y
pasa a ejecutarse después de que exista la tabla `rol`. El seed de `permiso` (`002-01`) se
queda donde está: no depende de `rol`.

**Alternativa descartada — reordenar los `include` del master changelog para que `005` corra
antes que `002`.** Arregla el síntoma y deja el mecanismo intacto: los changelogs pasan a
tener un orden que no es el de su número, y el siguiente que agregue un `010` lo pondrá al
final creyendo que el número manda. El fichero
`db.changelog-master.yaml` dice explícitamente «en orden creciente». Un orden numérico que
miente es la trampa §4.3 del playbook: el comentario promete la garantía contraria a la
del código.

**Alternativa descartada — crear la tabla `rol` en `001`.** Es la forma más limpia si se
pudiera reescribir la historia, pero `001` ya está aplicado en las BD existentes y `005`
quedaría como un changelog que migra desde un esquema que ya nadie produce. Se convierte en
código muerto que el próximo lector intentará entender. Sólo es viable si se acepta D2-B.

---

## D2 — Cómo se trata `005-fks.sql`, que migra desde un esquema que ya no existe

Los changesets `005-07` … `005-10` operan sobre un `rol_permiso` con columna `rol` varchar y
sin `rol_id`. El `001` actual crea `rol_permiso` con `rol_id BIGINT NOT NULL` y sin `rol`.
Contra una BD limpia, `005-07` falla; contra las BD ya migradas, esos changesets **ya están
aplicados** y su checksum está registrado.

Las dos rutas no son intercambiables y la elección depende de un hecho externo al código:
**si las bases de datos existentes se conservan o se recrean.**

### D2-A — Conservar las BD existentes (edición aditiva)

`005-07` … `005-10` se envuelven en `preConditions` con `onFail:MARK_RAN`, de modo que en BD
limpia se marcan como ejecutados sin correr, y en BD antiguas siguen constando aplicados. El
seed de `rol_permiso` va en un changelog nuevo `010-rbac-seed-rol-permiso.sql`, y el
`002-02` original se retira del changelog dejando registro de por qué.

- **A favor**: dev, staging y cualquier BD con datos siguen funcionando sin intervención.
- **En contra**: `005` queda como un archivo que documenta una migración fantasma. Hay que
  dejarlo escrito en el propio archivo, o alguien lo «limpiará» en seis meses.

### D2-B — Recrear las BD (consolidación)

`001` pasa a ser el esquema real —incluida la tabla `rol`— y `005` desaparece o se reduce a
las FKs que sí faltan. El seed de RBAC completo queda en `002`, ya con `rol` disponible.

- **A favor**: los changelogs vuelven a leerse como lo que dicen ser. Sin código muerto.
- **En contra**: **destruye los datos de toda BD existente.** Sólo es aceptable si no hay
  ninguna con datos que importen.

### Resuelto: **D2-B**, con evidencia medida

Inspeccionado el 2026-09-07 el volumen `electoral-postgres-dev-data` (contenedor
`electoral-postgres-dev`, parado desde hace 3 semanas), montado en un contenedor temporal
para no tocar el original:

```
usuario      0 filas
evidencia    0 filas
jrv          0 filas
candidato    0 filas
rol_permiso  29 filas   ← seed reproducible, no son datos de usuario
```

Y tres hechos que cierran la discusión:

1. **No existen las tablas del dominio de actas.** `acta`, `acta_detalle`, `audit_log`,
   `notificacion` y `observer_jrv_asignacion` no están en `pg_tables`. Con
   `spring.jpa.hibernate.ddl-auto=validate` (`application.properties:16`), la aplicación
   **no arranca** contra esta BD. Ya estaba muerta antes de este change.
2. **El changelog aplicado no es el que está en el repo.** `databasechangelog` registra
   `002-03-admin-user` como `EXECUTED`, pero ese changeset hoy está comentado en
   `002-rbac-seed.sql:97`. Y `001-03-rol_permiso` guarda el checksum
   `9:eaf5da0640c3290651a91f49e52d682e`, de la versión anterior a los commits `3009a15` y
   `d97c8c4` que reescribieron `001` en su sitio. Conservar esta BD significa arrastrar
   checksums que ya no corresponden a ningún archivo.
3. `009-acta-observer-permisos.sql` nunca se aplicó: la historia se detiene en `008-04`.

Staging no está desplegado (confirmado por el usuario), así que no hay una segunda BD que
proteger.

**Conclusión**: no hay datos que conservar, y la BD local no es recuperable de todos modos.
Se elige **D2-B — consolidación**. `tasks.md` está escrito sobre esta ruta.

---

## D3 — La idempotencia se prueba, no se supone

**Decisión**: el criterio de cierre incluye aplicar el changelog **dos veces seguidas** sobre
la misma BD sin error.

**Alternativa descartada — confiar en que los `ON CONFLICT DO NOTHING` y los `NOT EXISTS` ya
lo garantizan.** Los del `002` están bien escritos, pero un `ALTER TABLE ... ADD COLUMN` no
es idempotente y es exactamente el tipo de changeset que este change introduce. Una garantía
que nadie ejecuta es una garantía que no existe.

---

## D4 — CI corre los tests y se demuestra que la compuerta muerde

**Decisión**: `ci.yml` pasa de `./mvnw clean package -DskipTests` a ejecutar la suite. Y la
tarea no se da por cerrada hasta haber **roto un test a propósito y visto el job en rojo**.

**Alternativa descartada — quitar `-DskipTests` y dar por hecho que ya corren.** Es
precisamente el defecto §4.6 que este change existe para reparar: una compuerta que parece
comprobar algo. La única evidencia válida es haberla visto fallar.

**Alternativa descartada — dejar `continue-on-error: true` mientras se estabiliza.** Produce
un job verde con tests rojos: la misma mentira con otra sintaxis. Si la suite no puede estar
en verde, este change no está terminado.

### Sobre el servicio Postgres del workflow

`ci.yml` ya levanta un `postgres:16-alpine` como service. Los tests usan Testcontainers
(`spring-boot-testcontainers`, `testcontainers-postgresql` en `pom.xml`), que levanta **su
propio** contenedor e ignora ese service. Hay que decidir cuál sobra: mantener los dos es
arrancar un Postgres que nadie usa y confundir al próximo lector sobre de dónde sale la BD
de los tests. Se resuelve midiendo cuál usan realmente los tests, no leyendo.

---

## Lo que este change deja escrito para el futuro

En `db.changelog-master.yaml`, junto al comentario que ya pide orden creciente, queda la
regla que faltaba:

> Un changeset no puede referenciar una tabla o columna creada en un changelog posterior.
> Si `002` necesita algo de `005`, va después de `005` — no se reordenan los `include`.

Y la comprobación que lo detecta antes de un merge, en CI: los tests corren contra una BD
limpia, que es el único sitio donde este defecto es visible.
