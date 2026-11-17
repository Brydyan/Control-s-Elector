# Apply Progress — Configuración Geográfica Jerárquica

**Inicio:** [Fecha inicio aquí]  
**Cierre:** [Fecha cierre aquí]  
**Builder:** [Tu nombre aquí]

---

## Tareas Completadas

- [ ] A) Implementar Backend: Endpoint GET árbol completo
  - [ ] GeografiaService + query
  - [ ] GeografiaResource + endpoint
  - [ ] Tests unitarios
  - [ ] Tests seguridad (permisos)
  - Commits: 
  
- [ ] B) Implementar Backend: CRUD Cantones
  - [ ] CantonService (create/update/delete)
  - [ ] CantonResource (endpoints)
  - [ ] Validaciones (código único, check hijos)
  - [ ] Tests
  - Commits:
  
- [ ] C) Implementar Backend: CRUD Parroquias + JRVs
  - [ ] ParroquiaService + JRVService
  - [ ] Endpoints
  - [ ] Validaciones (FK, número único, cascada)
  - [ ] Tests
  - Commits:
  
- [ ] D) Implementar Frontend: Página + Tree
  - [ ] ConfiguracionGeograficaPage component
  - [ ] Template HTML con p-tree
  - [ ] SCSS responsive
  - [ ] Load + error states
  - [ ] Tests (Jasmine)
  - [ ] E2E (Playwright)
  - Commits:
  
- [ ] E) Implementar Frontend: Modales CRUD (4)
  - [ ] ModalCrearProvinciaComponent
  - [ ] ModalCrearCantonComponent
  - [ ] ModalCrearParroquiaComponent
  - [ ] ModalCrearJRVComponent
  - [ ] Validaciones frontend
  - [ ] Toast success/error
  - [ ] Tests (Jasmine)
  - Commits:
  
- [ ] F) Integración: Menú + Permisos + E2E
  - [ ] Menú: agregar "Configuración Geográfica"
  - [ ] Guard: ruta protegida MASTER only
  - [ ] Permisos backend: recurso 'configuracion', acción 'write'
  - [ ] E2E flow completo
  - Commits:

---

## Tests Ejecutados

**Backend:**
```bash
rtk mvn test
# Total tests BEFORE: 234
# Total tests AFTER: 234 + XX
# Resultado: ✅ / ⚠️ / ❌
```

**Frontend:**
```bash
rtk npm test
# Total tests BEFORE: 156
# Total tests AFTER: 156 + XX
# Resultado: ✅ / ⚠️ / ❌
```

---

## Mutaciones Verificadas

**Backend:**
- [ ] Task A: remover JOIN canton → test falla ✅
- [ ] Task B: remover validación código único → test falla ✅
- [ ] Task C: remover check "has children" → test falla ✅

**Frontend:**
- [ ] Task D: comentar loadTree() → E2E falla (árbol vacío) ✅
- [ ] Task E: comentar onConfirm() en modal → E2E falla (nada ocurre al crear) ✅

---

## Commits

- [ ] commit 1: Backend: GET /geografia endpoint (Task A)
- [ ] commit 2: Backend: CRUD cantones (Task B)
- [ ] commit 3: Backend: CRUD parroquias + JRVs (Task C)
- [ ] commit 4: Frontend: página + tree (Task D)
- [ ] commit 5: Frontend: 4 modales (Task E)
- [ ] commit 6: Integración: menú + guard (Task F)

---

## Blockers / TODOs Pendientes

- [ ] [Nombre del blocker si hay]
  - Impacto:
  - Workaround:

---

## Próximos Pasos

→ Listo para `/03-verify-sdd configuracion-geografica`
