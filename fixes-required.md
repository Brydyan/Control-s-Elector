# T-FE-18 Fixes Required

## Blocker #1: Duplicados en NAV_CONFIG

**Archivo**: `frontend/app/src/app/layout/nav.config.ts`

**Línea 89** (COORDINATOR sección Evidencias):
```typescript
{
  label: 'Evidencias',
  items: [
    {
      label: 'Subir evidencia',
      icon: 'pi pi-cloud-upload',
      route: '/coordinator/actas',  // ← ERROR: Duplicado con Actas L74
    },
  ],
},
```

**Línea 160** (MASTER sección Evidencias):
```typescript
{
  label: 'Evidencias',
  items: [
    {
      label: 'Subir evidencia',
      icon: 'pi pi-cloud-upload',
      route: '/coordinator/actas',  // ← ERROR: Duplicado con Actas L145
    },
  ],
},
```

**Análisis**: Ambas rutas apuntan a `/coordinator/actas`, que ya existe en la sección "Actas". Violación del contrato "ZERO duplicates".

**Causa probable**: Error de copy-paste. La intención es probablemente tener una ruta para "Subir evidencia" distinta a "Aprobar/Rechazar actas" (ambos L74 y L89 dicen `/coordinator/actas`).

**Opción recomendada**: 
- Cambiar L89 a `/coordinator/evidencias` (ruta placeholder que será validada/mapeada)
- Cambiar L160 a `/coordinator/evidencias` (similar)
- Luego validar contra PLACEHOLDER_ROUTES si es necesario

**Impacto**: Rompe tests nav.config.spec.ts líneas 90 y 152.

---

## Blocker #2: Mock setup TypeError en sidebar.component.spec.ts

**Archivo**: `frontend/app/src/app/layout/sidebar/sidebar.component.spec.ts`

**Línea 33-38** (setup de authStoreMock):
```typescript
authStoreMock = jasmine.createSpyObj('AuthStore', [], {
  user: jasmine.createSpy('user').and.returnValue({
    role: 'OBSERVER',
    email: 'test@example.com',
  }),
});
```

**Problema**: 
- El test crea `user` como una propiedad con un spy function como valor
- El componente (sidebar.component.ts L61) asigna `readonly user = this.store.user`
- Luego (L68) intenta llamar: `const role = this.user()?.role`
- Falla porque `this.user` es el valor del spy (un objeto user), no un callable signal

**Análisis**: El componente espera que `user` sea una signal (Angular 14+), que es callable. El test lo mockea como una propiedad con valor directo.

**Solución**: Cambiar el mock para que `user` sea un signal:

```typescript
authStoreMock = jasmine.createSpyObj('AuthStore', ['user']);
authStoreMock.user = signal({
  role: 'OBSERVER',
  email: 'test@example.com',
});
```

O si jasmine.createSpyObj no coopera, mockear signal directamente en TestBed provider:

```typescript
const userSignalMock = signal({
  role: 'OBSERVER',
  email: 'test@example.com',
});

authStoreMock = {
  user: userSignalMock,
};
```

**Luego en setUserRole()** (L55-60):
```typescript
const setUserRole = (role: 'OBSERVER' | 'COORDINATOR' | 'ADMIN' | 'MASTER') => {
  (authStoreMock.user as any).set({
    role,
    email: `${role.toLowerCase()}@example.com`,
  });
};
```

**Impacto**: Rompe 15/16 tests de sidebar.component.spec.ts.

---

## Blocker #3: Format check fallido

**Archivo**: `frontend/app/src/app/layout/sidebar/sidebar.component.spec.ts`

**Comando**: `pnpm format:check`

**Error**:
```
[warn] src/app/layout/sidebar/sidebar.component.spec.ts
Code style issues found in the above file. Run Prettier with --write to fix.
```

**Solución**:
```bash
cd frontend/app
pnpm format
```

Luego verificar:
```bash
pnpm format:check
```

**Impacto**: Bloquea gate de formato.

---

## Verification Checklist

Tras aplicar estos fixes:

- [ ] Ejecutar `pnpm lint` → exit 0
- [ ] Ejecutar `pnpm format:check` → exit 0
- [ ] Ejecutar `pnpm test:ci -- nav.config.spec.ts --run` → 9/9 PASS
- [ ] Ejecutar `pnpm test:ci -- sidebar.component.spec.ts --run` → 16/16 PASS
- [ ] Ejecutar `pnpm build` → exit 0
- [ ] Ejecutar suite completo `pnpm test:ci --run` → todos PASS (593 → 610)

