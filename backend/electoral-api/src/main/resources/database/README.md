# `database/` — migraciones y seed (Liquibase)

Changelogs de Liquibase para el schema y los datos de referencia de electoral-api.

> **Estado**: activo. Liquibase crea el schema y los datos de referencia.
> El schema lo generan los changelogs; Hibernate corre en `validate`.

## Estructura

| Archivo | Qué hace |
|---|---|
| `db.changelog-master.yaml` | Orquestador. Incluye los changelogs en orden explícito. |
| `changelog/001-initial-schema.sql` | Tablas: `usuario`, `permiso`, `rol_permiso`, `sesion`, `evidencia`. |
| `changelog/002-rbac-seed.sql` | Data de referencia: 13 permisos + matriz `rol_permiso`. |

## Convenciones

- **Formato**: `--liquibase formatted sql`. Un `-- changeset autor:id` por cambio atómico.
- **IDs**: `NNN-MM-<nombre>` — `NNN` = número de changelog, `MM` = orden dentro del archivo.
- **Orden**: nuevos changelogs se numeran creciente (`003-...`) y se agregan al final
  del master. Nunca se intercalan.
- **Inmutabilidad**: un changeSet ya aplicado en un entorno compartido NO se edita
  (rompe el checksum). Para corregir → changeSet nuevo.
- **Rollback**: cada changeSet incluye su línea `-- rollback ...`.
- **Idempotencia**: los seeds usan `ON CONFLICT` / `NOT EXISTS` para poder re-correr sin romper.

## Agregar un cambio de schema

1. Modificar la entidad JPA.
2. Crear `changelog/003-<descripcion>.sql` con el/los changeSet.
3. Agregar el `include` al final de `db.changelog-master.yaml`.
4. Arrancar la app → Liquibase aplica lo pendiente; Hibernate (`validate`) verifica el match.
