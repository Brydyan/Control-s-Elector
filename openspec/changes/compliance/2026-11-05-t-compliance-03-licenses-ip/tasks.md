# T-COMPLIANCE-03: Tasks — Licenses & IP Attribution

**Effort:** 2-3 puntos → 1 sprint

---

## Sprint 1: Licenses Page + Attribution

### Task 3.1 — Licenses Page (Frontend)

**Estimación:** 1 pt

- [ ] **C.3.1.1** — LicensesComponent
  - Path: `frontend/src/app/legal/licenses/licenses.component.ts`
  - Route: `/licenses` (public)
  - Load licenses from `assets/legal/licenses.json`

- [ ] **C.3.1.1 — Mutación**
  - Delete route
  - Test must fail
  - Note: [test name]
  - Restore

- [ ] **C.3.1.2** — Table with expandable license text
  - Columns: Library, Version, License, Link
  - Click row → license text expands
  - GPL/AGPL: special styling (warning color)

- [ ] **C.3.1.2 — Mutación**
  - Remove GPL warning styling
  - Visual regression test should fail
  - Note: [test name]
  - Restore

---

### Task 3.2 — Generate licenses.json

**Estimación:** 1 pt

- [ ] **C.3.2.1** — npm run licenses:build script
  - Path: `frontend/package.json` script
  - Reads package.json → generates licenses.json
  - Includes version, license type, homepage

- [ ] **C.3.2.1 — Mutación**
  - Delete license entry from generated file
  - Test: COUNT must be -1
  - Note: [test name]
  - Restore

---

### Task 3.3 — Assets Attribution

**Estimación:** 0.5 pts

- [ ] **C.3.3.1** — assets-attribution.json
  - Path: `frontend/src/assets/legal/assets-attribution.json`
  - Format: [{asset, source, license, url, author}]
  - Display in /licenses under "Icons & Assets"

- [ ] **C.3.3.1 — Mutación**
  - Remove 1 asset
  - Render test should show -1 items
  - Note: [test name]
  - Restore

---

## Gates

```bash
npm run licenses:build
npm run test -- --code-coverage
grep -r "licenses" frontend/src/app/app.module.ts
```

**Last Updated:** 2026-11-05
