# Tasks — Configuración Geográfica Jerárquica

**Versión:** 1.0  
**Estado:** 📋 Especificación de Tareas

---

## A) Implementar Backend: Endpoint GET árbol completo

**Descripción:**
Crear endpoint `GET /api/v1/master/geografia` que devuelve toda la geografía en estructura jerárquica para renderizar en árbol.

**Criterios de Aceptación:**
- [ ] Endpoint devuelve 200 con árbol completo (ver spec backend.md)
- [ ] Response incluye: id, nombre, codigo, children recursivo
- [ ] Response válido para 0, 1, 10+ provincias
- [ ] Permisos: solo MASTER + ADMIN (read)

**Criterio de Aceptación por Mutación:**
- [ ] Remover JOIN a canton en la query → test debe fallar (falta cantones)
- [ ] Remover WHERE parroquias.canton_id = ... → test debe fallar (parroquias sueltas)

**Tests:**
- [ ] GeografiaServiceTest: testGetArbolCompleto() ✅ 1-3 provincias
- [ ] GeografiaResourceTest: testGetGeografia_200() ✅ response shape
- [ ] SecurityTest: testGetGeografia_ForbiddenSinPermiso() ✅ OBSERVER no ve

---

## B) Implementar Backend: CRUD Cantones

**Descripción:**
Endpoints: POST /cantones, PUT /cantones/{id}, DELETE /cantones/{id}

**Criterios de Aceptación:**
- [ ] POST: crear cantón bajo provincia, 201 response
- [ ] POST: validar código único por provincia (409 si existe)
- [ ] PUT: editar nombre/código, 200 response
- [ ] DELETE: retorna 204 exitoso
- [ ] DELETE: retorna 409 si tiene parroquias hijas

**Criterio de Aceptación por Mutación:**
- [ ] Remover validación de código único → test crear 2 con mismo código debe fallar
- [ ] Remover check "has children" en DELETE → test eliminar cantón con parroquias debe pasar incorrectamente

**Tests:**
- [ ] CantonServiceTest: testCrearCantonValido() ✅
- [ ] CantonServiceTest: testCrearCantonCodigoDuplicado_throws() ✅
- [ ] CantonServiceTest: testEliminarCantonConParroquias_409() ✅
- [ ] CantonResourceSecurityTest: solo MASTER puede POST/PUT/DELETE ✅

---

## C) Implementar Backend: CRUD Parroquias + JRVs

**Descripción:**
Endpoints: POST/PUT/DELETE /parroquias, POST/PUT/DELETE /jrvs

**Criterios de Aceptación:**
- [ ] Crear parroquia bajo cantón específico (FK canton_id requerido)
- [ ] Crear JRV bajo parroquia específica (FK parroquia_id requerido)
- [ ] Eliminar parroquia: 409 si tiene JRVs
- [ ] Eliminar JRV: 409 si tiene actas registradas
- [ ] Número de JRV único en sistema

**Tests:**
- [ ] ParroquiaServiceTest: testCrearParroquiaValida() ✅
- [ ] JRVServiceTest: testEliminarJRVConActas_409() ✅
- [ ] ValidationTest: numero JRV único ✅

---

## D) Implementar Frontend: Página + Tree Component

**Descripción:**
Crear página `/master/configuracion-geografica` con p-tree de PrimeNG, expandir/colapsar, toolbar.

**Criterios de Aceptación:**
- [ ] Página carga y llama GET /api/v1/master/geografia
- [ ] Árbol renderiza correctamente provincias → cantones → parroquias → jrvs
- [ ] Click en nodo: se expande/colapsa con animación
- [ ] Botones "Expandir Todo" / "Colapsar Todo" funcionan
- [ ] Keyboard: ↑ ↓ → ← navega árbol (PrimeNG nativo)
- [ ] Error state: si GET falla, muestra banner + retry button
- [ ] Loading: spinner mientras carga

**Tests (E2E):**
- [ ] E2E: load page, tree visible with 1+ provincias ✅
- [ ] E2E: expand provincia, hijos visibles ✅
- [ ] E2E: "Expandir Todo" expande all nodos ✅

---

## E) Implementar Frontend: Modales CRUD (4 componentes)

**Descripción:**
Crear 4 modales: crear provincia/canton/parroquia/jrv, con validaciones frontend.

**Criterios de Aceptación:**
- [ ] Modal crear provincia: campos nombre, codigo; validar no vacíos
- [ ] Modal crear cantón: recibe provinciaId, crea bajo esa provincia
- [ ] Modal crear parroquia: recibe cantonId, campo es_urbana (toggle)
- [ ] Modal crear JRV: recibe parroquiaId, campos numero, recinto, total_electores
- [ ] Submit: llama endpoint correspondiente, en error muestra toast
- [ ] Success: cierra modal, toast "creado exitosamente", recarga árbol
- [ ] Context menu en nodos: acciones editar/eliminar disponibles

**Tests:**
- [ ] ModalCrearCantonComponent: testSubmit_calls_POST() ✅
- [ ] ModalCrearCantonComponent: testValidacion_nombreVacio_error() ✅
- [ ] ModalCrearJRVComponent: testEditarJRV_calls_PUT() ✅

---

## F) Integración: Menú + Permisos + E2E

**Descripción:**
Agregar entrada en menú admin, integrar permisos, E2E flow completo.

**Criterios de Aceptación:**
- [ ] Menú admin: "Configuración Geográfica" visible para MASTER
- [ ] Menú admin: oculto para ADMIN, COORDINATOR, OBSERVER
- [ ] Ruta protegida: /master/configuracion-geografica requiere MASTER role
- [ ] E2E full flow: MASTER login → navega a Configuración Geográfica → expande provincia → crea cantón → ve en árbol

**Tests:**
- [ ] SecurityTest: solo MASTER ve menú item ✅
- [ ] E2E: MASTER crea cantón exitosamente ✅
- [ ] E2E: OBSERVER GET 403 si accede ruta directo ✅

---

## Qué NO Hacer en Esta Fase

- ❌ Importación masiva desde CSV (post-MVP, será SDD separada)
- ❌ Replicación automática de geografía entre ambientes
- ❌ Webhooks/notificaciones cuando geografía cambia
- ❌ Historial de cambios (audit trail) — nice-to-have, posponer

---

## Compuertas antes de Dar por Terminada esta Fase

**Backend:**
```bash
# Todos los tests pasan
rtk mvn test

# Endpoints responden correctamente
curl -X GET http://localhost:8080/api/v1/master/geografia
# → 200 con árbol

# Validaciones funcionan
curl -X POST http://localhost:8080/api/v1/master/cantones \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Test","codigo":"9999","provincia_id":1}'
# → 201 (OK) o 409 (si código existe)
```

**Frontend:**
```bash
# Tests pasan
rtk npm test

# App levanta sin errores
rtk npm start
# → http://localhost:4200/master/configuracion-geografica carga

# Tree visible + clickeable
# Expandir nodo → hijos visibles
# Crear cantón → aparece en árbol
```

---

## Próximos Pasos

→ Pasar a **apply-progress.md** para Builder documentar el progreso
