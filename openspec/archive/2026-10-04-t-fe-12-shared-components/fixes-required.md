# Fixes Required — T-FE-12: Componentes Compartidos (Ronda 4 — CERRADO)

**Fecha:** 2026-10-04  
**Rama:** `brydyan/sc-174/t-fe-12-componentes-compartidos`  
**HEAD:** `161ef78cd6d5234744eb35c8a08e9589d1b62224`  
**Estado:** **0 BLOCKERS / 0 CRITICAL FINDINGS (TODOS LOS FIXES VERIFICADOS Y CERRADOS)**

---

## Estado de Bloqueadores Previos

Todos los hallazgos críticos de rondas anteriores han sido subsanados y verificados con pruebas y mutaciones ejecutadas en vivo en Ronda 4:

### CRITICAL 1 — Build fails: `[label]` no es una propiedad conocida de `app-button` en `ConfirmDialogComponent`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. `ConfirmDialogComponent` en `frontend/app/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts` proyecta `{{ cancelLabel }}` y `{{ confirmLabel }}` dentro de `<app-button>...</app-button>` a través de `<ng-content>`.
  2. `confirm-dialog.component.spec.ts` cuenta con el test `should render label text inside each button via ng-content (CRITICAL 1 fix)` comprobando el contenido de texto en el DOM:
     - `cancelBtn.textContent.trim() === 'No, salir'`
     - `acceptBtn.textContent.trim() === 'Sí, aprobar'`
  3. `pnpm build` compila con exit 0 sin emitir error `NG8002`.

---

### CRITICAL 2 — Build fails: `appFocusTrap` type mismatch `string` → `boolean` en `ConfirmDialogComponent`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. En `confirm-dialog.component.ts:29`: `<div class="modal-card" [appFocusTrap]="true">` realiza binding booleano explícito.
  2. `pnpm build` compila con exit 0 sin emitir error `TS2322`.

---

### CRITICAL 3 — Escenario S2 sin cobertura: falta `rol-badge.component.spec.ts`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. Archivo `frontend/app/src/app/shared/components/rol-badge/rol-badge.component.spec.ts` activo con 5 tests unitarios.
  2. Cobertura completa de roles:
     - `COORDINATOR` → `'CORREGIDA'`
     - `MASTER` → `'APROBADA'`
     - `ADMIN` → `'ENVIADA'`
     - `OBSERVER` → `'default'`
  3. Mutación en vivo: al alterar `MASTER` para retornar `'default'`, el test `RolBadgeComponent (T-FE-12 S2) > should map MASTER to APROBADA variant` cae inmediatamente (exit 1). Restauración limpia con respaldo `.bak`.

---

### WARNING 1 — Aserción estructural en `estado-badge.component.spec.ts`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. Test unitario directo en `estado-badge.component.spec.ts` validando el mapeo de los estados electorales (`APROBADA`, `EN_DISPUTA`, `CORREGIDA`, `BORRADOR`, `ANULADA`).
  2. Aserción DOM incluida: `should render app-badge in DOM with mapped variant` comprueba que `<app-badge>` existe en el DOM tras renderizado.

---

### WARNING 2 — Alias de importación `@shared/*` ausente en `tsconfig.json` y `tsconfig.app.json`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. `baseUrl: "./"` y `"paths": { "@shared/*": ["src/app/shared/*"] }` configurados en `tsconfig.json` y `tsconfig.app.json`.
  2. Alias `@shared` configurado en `vite.config.ts`.
  3. Consumido exitosamente en `acta-detalle.page.ts` y compilado sin fallas.

---

### FIX COLATERAL — `dashboard.page.html` `[description]` inválido en `app-error-state`

- **Estado:** **RESUELTO Y VERIFICADO**
- **Verificación Ronda 4:**
  1. `dashboard.page.html:10` utiliza `[message]="err"`.
  2. `pnpm build` no emite ningún error de template en `DashboardPage`.

---

## Conclusión

**Total de bloqueadores activos:** 0.  
El código cumple con todas las compuertas y criterios requeridos.
