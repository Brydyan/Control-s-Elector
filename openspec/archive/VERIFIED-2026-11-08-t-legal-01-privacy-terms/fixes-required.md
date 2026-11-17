# Fixes Required — SDD T-LEGAL-01 (Verify Round 2)

**Status:** 🔴 **REWORK** (2 CRITICAL frontend test failures)

---

### CRITICAL 1 — AuthStore missing legal acceptance methods

1. **Síntoma:** Frontend test fails: `TypeError: this.store.setAcceptedTermsVersion is not a function`
2. **Causa raíz:** `TermsAcceptanceService` (line 80) calls `this.store.setAcceptedTermsVersion(version)`, but AuthStore does not implement this method.
3. **Reproducción:**
   ```bash
   cd frontend/app && pnpm test -- src/app/features/legal/services/terms-acceptance.service.spec.ts
   # FAIL: aceptar guarda la version en el store y en la sesion
   ```
4. **Fix propuesto:**
   - Add to `AuthStore` (or `UserStore` if that's where auth state lives):
   ```typescript
   setAcceptedTermsVersion(version: string): void {
     this.acceptedTermsVersion.set(version);
   }

   getAcceptedTermsVersion(): string | null {
     return this.acceptedTermsVersion();
   }

   setAcceptedPrivacyVersion(version: string): void {
     this.acceptedPrivacyVersion.set(version);
   }

   getAcceptedPrivacyVersion(): string | null {
     return this.acceptedPrivacyVersion();
   }
   ```
5. **Verificación de cierre:**
   - Test passes: `pnpm test -- src/app/features/legal/services/terms-acceptance.service.spec.ts`
   - No error about missing method

---

### CRITICAL 2 — TermsAcceptanceModalComponent missing button rendering

1. **Síntoma:** Test expects text "Acepto" in DOM but it's not rendered. Modal text visible but button not found.
2. **Causa raíz:** Component template missing `<button>` element or button is not rendered in test (CSS hidden, *ngIf condition false, etc.)
3. **Reproducción:**
   ```bash
   cd frontend/app && pnpm test -- src/app/features/legal/components/terms-acceptance-modal/terms-acceptance-modal.component.spec.ts
   # FAIL: no tiene boton de rechazo: la unica accion es aceptar
   # Expected to contain: "Acepto"
   # Actual DOM text: "Términos de Uso Actualizados Para seguir usando..."
   ```
4. **Fix propuesto:**
   - Check component template (`.html`):
   ```html
   <ion-modal [isOpen]="isOpen">
     <ion-content>
       <p>Términos de Uso Actualizados...</p>
       <!-- ADD THIS: -->
       <button ion-button (click)="onAccept()">Acepto</button>
     </ion-content>
   </ion-modal>
   ```
   - Or if button exists but not rendering, check:
     - Is `*ngIf` blocking it?
     - Is CSS `display: none` hiding it?
     - Is button outside the modal container?
5. **Verificación de cierre:**
   - Test passes: `pnpm test -- src/app/features/legal/components/terms-acceptance-modal/terms-acceptance-modal.component.spec.ts`
   - Text "Acepto" appears in rendered DOM

---

## Summary

**2 CRITICAL blockers must be fixed before next verify round:**

| Issue | Files | Action |
|-------|-------|--------|
| Store methods missing | `AuthStore` or `UserStore` | Add 4 methods (set/get terms version, privacy version) |
| Modal button missing | `terms-acceptance-modal.component.html` | Add or fix button rendering |

**Backend:** ✅ Fixed and passing (82 tests)  
**Frontend:** 🔴 2 test failures in legal domain (684/686 passing)

After fixes, run:
```bash
pnpm test:ci && pnpm lint && pnpm format:check
```

Then re-trigger Verify.
