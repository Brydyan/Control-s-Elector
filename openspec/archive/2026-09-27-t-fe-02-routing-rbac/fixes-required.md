# Fixes Required — T-FE-02 Routing con RBAC Guards

**Verificación fecha**: 2026-09-27  
**Estado**: FAIL — 2 CRITICAL bloqueadores  
**Rama**: brydyan/sc-164/t-fe-02-routing-con-rbac-guards

---

## CRITICAL 1 — auth.guard.spec.ts no existe (S1, S2, S3 untested)

1. **Síntoma**  
   `find frontend/app/src -name "*guard*spec*"` retorna sin output. Los escenarios S1 (sin token → redirect /login), S2 (OBSERVER token → authGuard true), S3 (ADMIN token + ruta MASTER → roleGuard false, redirect /dashboard) no tienen cobertura de test automatizado. `pnpm test:ci` reporta 118 tests pasados, ninguno para guards.

2. **Causa raíz**  
   apply-progress.md documenta que los guards (`authGuard`, `roleGuard`, `guestGuard`) fueron implementados antes del ciclo SDD. El builder no generó tests asumiendo "el spec ya está implementado". Bajo Strict TDD Mode, la existencia del código no exime la necesidad de tests automatizados para cada escenario.

3. **Reproducción**  
   ```bash
   cd frontend/app
   find src -name "*guard*spec*"  # sin output
   pnpm test:ci 2>&1 | grep -i guard  # sin matches
   ```

4. **Fix propuesto**  
   Crear archivo `src/app/core/auth/auth.guard.spec.ts` con 3 test cases:
   - **S1**: `authGuard` sin token en localStorage → retorna `UrlTree('/login')`
   - **S2**: `authGuard` + `roleGuard` con token OBSERVER válido → ambos retornan `true`
   - **S3**: `roleGuard` con token ADMIN en ruta protegida con `roles: ['OBSERVER','COORDINATOR']` → retorna `UrlTree('/dashboard')`

5. **Verificación de cierre**  
   ```bash
   cd frontend/app
   pnpm test:ci  # debe incluir auth.guard.spec.ts con PASS
   grep "auth.guard.spec" /tmp/test-output.log  # confirma ejecución
   ```
   Todos los casos deben pasar en verde.

---

## CRITICAL 2 — S4 (lazy-load chunks) sin test automatizado

1. **Síntoma**  
   Ningún archivo `*.spec.ts` contiene test que verifique el comportamiento de carga dinámica de chunks. apply-progress.md documenta la verificación como "manual en DevTools inspeccionando Network tab". Bajo Strict TDD, verificación manual no es válida.

2. **Causa raíz**  
   La configuración de `loadChildren` en `app.routes.ts` existe y funciona (chunks se cargan correctamente en navegador real), pero no hay test automatizado que lo demuestre. `grep -r "loadChildren\|lazy" frontend/app/src --include="*.spec.ts"` retorna sin output.

3. **Reproducción**  
   ```bash
   cd frontend/app
   grep -r "loadChildren\|lazy" src --include="*.spec.ts"  # sin output
   # Verificar que las rutas lazy existen en src/app/app.routes.ts:
   grep "loadChildren:" src/app/app.routes.ts | head -3  # muestra /observer, /coordinator, /master
   ```

4. **Fix propuesto**  
   Crear test de integración en `src/app/core/auth/auth.guard.spec.ts` o archivo dedicado `src/app/app.routes.spec.ts`:
   - Test: `provideRouter(routes)` en TestBed
   - Inject `Router` y `Location`
   - `router.navigate(['/observer/mis-jrvs'])` (ruta lazy)
   - Spy en `import()` o verificar que la ruta config contiene `loadChildren` (no `component`)
   - Assert: ruta se navega sin error, chunk no está precargado

5. **Verificación de cierre**  
   ```bash
   cd frontend/app
   pnpm test:ci  # test de lazy-load debe existir y pasar
   # Validar que todos los escenarios lazy están cubiertos:
   # /observer/mis-jrvs (observer.routes), /coordinator/disputas (coordinator.routes), /master/config (master.routes)
   ```
   Test debe estar en verde.

---

## Warnings (no bloqueantes, para referencia)

### W1 — TDD Cycle Evidence table ausente de apply-progress.md
Strict TDD exige tabla RED/GREEN/TRIANGULATE/SAFETY NET/REFACTOR en apply-progress. Documentar el ciclo seguido para guards.

### W2 — data.roles vs data.allowedRoles (divergencia cosmética)
roleGuard lee `route.data?.['roles']`, spec menciona `data.allowedRoles`. Funcional: todas las rutas usan `data: { roles: [...] }` consistentemente. Bajo prioridad.

### W3 — roleGuard redirige a /dashboard (vs / en design.md)
Design.md D4 dice roleGuard retorna false → Angular redirige a `/`. Implementación redirige a `/dashboard`. Functionally equivalent (root route tiene `redirectTo: 'dashboard'`). Bajo prioridad.

### W4 — Guards en mismo archivo, no en subdirectorio guards/
Design.md D2 dice `core/auth/guards/auth.guard.ts`. Implementación: `core/auth/auth.guard.ts` (sin subdirectorio). Consistente con T-FE-01 (decisión de no usar subdirectories). Documentado, no cambiar.

---

## Summary

| Item | Count | Status |
|------|-------|--------|
| CRITICAL bloqueadores | 2 | Arreglo requerido para PASS |
| Warnings | 4 | FYI, no bloquean |
| Tests a agregar | 4 spec cases + 1 lazy-load test | ~200 líneas estimadas |
| Archivo a crear | `auth.guard.spec.ts` | core/auth/ |

**Next step**: Implementar fixes C1 y C2, luego re-ejecutar `sdd-verify` para confirmar PASS.
