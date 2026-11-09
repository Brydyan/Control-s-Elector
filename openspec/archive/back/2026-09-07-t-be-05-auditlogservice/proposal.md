# Proposal — T-BE-05 · AuditLogService

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-05.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`AuditLogService.registrar(...)`, el único camino por el que debe escribirse en `audit_log`.

## Por qué importa más que la mayoría

En un sistema de observación electoral, el registro de auditoría **es** el producto tanto
como los resultados. Si alguien impugna un acta anulada, lo que decide es quién la anuló,
cuándo, desde qué IP y con qué motivo. Un `audit_log` incompleto no se puede reconstruir
después.

`CLAUDE.md` lo fija como regla dura: `API → AuditLogService → JPA → PG`, nunca directo.

## Estado real

| | |
|---|---|
| `AuditLogService` existe | sí |
| `ActaService` lo usa en registrar, disputar, corregir, aprobar, anular | sí |
| `EvidenciaService` lo usa | **no. Ni en upload ni en delete** |
| `AuthService` lo usa en login/logout | **verificar** — no aparece en el código leído |
| Tests que cubran la auditoría | **ninguno** |

Es decir: la regla se cumple en un módulo y no en su vecino (§4.2), y **nada la protege**.
Ningún test cuenta filas de `audit_log`. Si mañana alguien borra la llamada de
`ActaService.anular`, la suite sigue verde.

## Los dos huecos concretos

**1. Evidencia no se audita.** Subir la foto de un acta y borrarla son las dos operaciones
que pueden destruir la prueba física de un resultado. Hoy no dejan rastro. Se repara en
`t-be-14-storage-acta` (D5); aquí queda la garantía escrita.

**2. La regla es de convención, no verificable.** Nada impide que un servicio inyecte
`AuditLogRepository` y escriba directo. La regla vive en `CLAUDE.md` y en la disciplina de
quien escribe.

## Alcance

- Escribir la garantía: qué debe quedar auditado y con qué campos.
- Verificar por mutación que existe al menos un test que lo protege — y si no existe,
  decirlo, que es el resultado esperado.
- Proponer cómo hacer verificable la regla de «nunca directo».

## Fuera de alcance

- **No** se añade la auditoría de evidencia: es `t-be-14-storage-acta`.
- **No** se cambia el modelo de `AuditLog`: es T-BE-03.

## Criterio de cierre

1. Existe al menos un test que cuenta filas de `audit_log` tras una acción, y **cae** al
   quitar la llamada.
2. El inventario de qué se audita y qué no está escrito y es correcto.
3. La propuesta para hacer verificable «nunca directo» está escrita, aunque no se implemente.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
