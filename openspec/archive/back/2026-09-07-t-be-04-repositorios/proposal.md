# Proposal — T-BE-04 · Repositorios JPA

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-04.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

Las interfaces de acceso a datos: `UsuarioRepository`, `JrvRepository`, `ActaRepository`,
`ActaDetalleRepository`, `AuditLogRepository`, `NotificacionRepository`,
`RefreshTokenRepository`, `SesionRepository`, `RolPermisoRepository`.

## Por qué merece verificación propia

Aquí vive `findActaActivaConLock`, el `@Lock(PESSIMISTIC_WRITE)` del que depende que no haya
dos actas para la misma mesa y el mismo cargo. Es una anotación de una línea que puede dejar
de tener efecto **sin ningún error**: si el método se llama fuera de una transacción, o si
alguien lo sustituye por `findActaActiva`, todo compila y todo pasa.

Es exactamente la clase de garantía que el playbook llama «un lock que existe y no bloquea es
peor que no tenerlo: parece resuelto».

## Estado real

| Repositorio | Tests |
|---|---|
| `UsuarioRepository` | 3, todos en ERROR |
| `MenuRepository`, `MenuPermisoRepository`, `PermisoRepository`, `RolPermisoRepository` | 3 cada uno, todos en ERROR |
| `EvidenciaRepository` | 3, en ERROR |
| `ActaRepository` | **ninguno** |
| `ActaDetalleRepository` | **ninguno** |
| `AuditLogRepository`, `NotificacionRepository` | **ninguno** |

El repositorio del que depende la unicidad de acta es de los que no tienen ni un test.

## Hallazgos que este change tiene que resolver

**1. `findWithFilters` no filtra por cantón**, y `backend.md:192` lo promete. Se trata en
`t-be-12-cierre-coordinacion`; aquí queda registrado como parte del contrato del repositorio.

**2. `RefreshTokenRepository` es código muerto** si `RefreshToken` lo es (ver
`t-be-01-entidades-base`, RF-USR-05).

**3. `ObserverJrvAsignacionRepository` está marcado `@Deprecated`** tras la decisión de que
el OBSERVER elige JRV libremente. Un repositorio deprecado que nadie borra acaba usándose.

## Alcance

- Verificar por mutación las queries que sostienen garantías: el lock, el filtro de ANULADA,
  el filtro por OBSERVER.
- Decidir qué se hace con los dos repositorios muertos.

## Fuera de alcance

- **No** se añaden queries nuevas: las del dashboard son T-BE-13.
- **No** se toca `ActaService`: es T-BE-06.

## Criterio de cierre

1. `findActaActivaConLock` tiene un test de concurrencia real que **cae** si se le quita el
   `@Lock`. (Se implementa en `t-be-15-tests-integracion`; aquí se verifica que existe.)
2. Cada garantía del spec tiene su mutación ejecutada.
3. Los repositorios muertos están borrados o justificados por escrito.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`. El test de concurrencia lo aporta
`back/2026-09-07-t-be-15-tests-integracion`.
