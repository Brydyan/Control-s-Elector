# Fixes Required — T-AUTH-INV-02

## Bloqueadores Detectados

**NINGUNO.** 

El implementador completó todas las tareas, verificó cada mutación, ejecutó los tests de punta a punta y produjo números que coinciden con los esperados.

---

## Notas de verificación

- ✅ Backend: 92 tests, 0 failures (matches apply-progress.md)
- ✅ Frontend: 767 tests, 0 failures (matches apply-progress.md)
- ✅ E2E: 51 passed + 1 skipped en Desktop; Mobile skipped j7 por layout preexistente (no bloqueador de este change)
- ✅ RLS: FORCE activada en user_invitations
- ✅ Autorización: presente en UserInvitationResource.invitar() línea 56
- ✅ Rate limiter: implementado y ejecutado antes del servicio
- ✅ Mutaciones: M1-M5, M7-M12 presentes; M6 sobrevive por diseño (dos capas)
- ✅ Formato: spotless:check + Prettier sin violaciones
- ✅ Migraciones: 029-user-invitations.sql aplicada (4 changesets)
- ✅ Email: destinatario ≠ username (email personal vs login)
- ✅ Estado de usuario: PENDIENTE_INVITACION con login bloqueado

## Desviaciones Permitidas (según apply-progress.md)

1. **Contraseña temporal NO implementada** — Documentado en D.2 con razones: cuenta nace bloqueada, no hay pantalla para cambiarla, clave que viaja por email es insegura.
2. **Email del login sin punto** — `juagarobs@control.ttcs` en lugar de `juagar.obs@control.ttcs`. Matches el generador del repo y los seeds existentes.
3. **Layout mobile** — barra fija tapa el botón de invitar en viewports <393px. Es defecto preexistente, no de este change. E2E skipped apropiademente.

## Recomendación

**MERGE READY.** No hay bloqueadores. El trabajo está listo para integración.
