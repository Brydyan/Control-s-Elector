# ODD #506: Evidencias Upload + Viewer Frontend

## Contexto

Backend para evidencias fotográficas **YA EXISTE y ESTÁ VERIFICADO:**
- ✅ T-BE-16: "observaciones-evidencia" (2026-09-25, verified, 302 tests)
- ✅ T-BE-18: "coordinator-evidencia-upload" (2026-09-25, verified, 309 tests)

Frontend tiene **rutas pero son PlaceholderPage:**
- ❌ `/evidencias` — Visor de evidencias (placeholder)
- ❌ `/evidencias/subir` — Subir evidencia (placeholder)

**Esta ODD:** Implementar UI frontend que consuma endpoints backend existentes.

---

## 1. Alcance (Tasks A-E)

### A — EvidenciasViewerComponent

**Aceptación:**
- [ ] Componente renderiza tabla/galería de evidencias
- [ ] GET /api/v1/evidencias con filtros (estado, tipo, fecha)
- [ ] Roles: OBSERVER, COORDINATOR, MASTER, ADMIN (todos leen)
- [ ] Estados visuales: PENDIENTE (amarillo), APROBADA (verde), RECHAZADA (rojo), CON_OBSERVACIONES (azul)
- [ ] Botón "Descargar" → GET /api/v1/evidencias/{id}/download-url
- [ ] Modal preview: mostrar imagen grande + observaciones

**Test:** E2E: Observer → /evidencias → ve sus propias fotos (GET filtrado por usuario)

---

### B — EvidenciasUploadComponent

**Aceptación:**
- [ ] Formulario de upload con input file (imagen)
- [ ] Endpoint: POST /api/v1/observer/actas/{id}/imagen (para Observer)
- [ ] Endpoint alternativa: POST /api/v1/coordinator/actas/{id}/imagen (para Coordinator)
- [ ] Progress indicator mientras carga
- [ ] Validación: archivo <5MB, JPG/PNG/WebP solo
- [ ] Respuesta: muestra #acta + estado (PENDIENTE review)
- [ ] Error handling: red de red, archivo corrupto, timeout

**Test:** E2E: Observer → /evidencias/subir → selecciona foto → envía → aparece en lista (estado PENDIENTE)

---

### C — EvidenciaService (Frontend)

**Aceptación:**
```typescript
// Métodos que implementar:
getEvidencias(filtros: EvidenciaFilters): Observable<Page<EvidenciaDTO>>
getEvidencia(id: string): Observable<EvidenciaDTO>
uploadEvidencia(actaId: string, file: File): Observable<EvidenciaDTO>
downloadEvidencia(id: string): Observable<Blob>
updateEstado(id: string, nuevoEstado: string): Observable<EvidenciaDTO>
```

**Test:** Unit tests: mock HTTP, verificar parámetros, error handling

---

### D — EvidenciasPage (Layout + Routing)

**Aceptación:**
- [ ] Página principal con tabs: "Galería" (viewer) + "Subir" (uploader)
- [ ] Rutas: `/evidencias` (viewer), `/evidencias/subir` (uploader)
- [ ] Header: "Evidencias Fotográficas · Actas electorales"
- [ ] Navegación back a acta detail (si viene de /coordinator/actas/:id)
- [ ] Responsive: mobile-first, sticky header en scroll

**Integración:** Reutilizar componentes de ODD #505 (Design System): Toast notificaciones, spacing tokens, dark mode

---

### E — Tests + E2E

**Aceptación:**
- [ ] Unit tests (componentes + service): >70% coverage
- [ ] E2E (Playwright):
  - Observer sube foto → aparece en galería
  - Coordinator ve foto de Observer → aprueba → estado cambia a APROBADA
  - Búsqueda por acta-id filtra correctamente

---

## 2. Dependencias

**Backend:** ✅ T-BE-16 + T-BE-18 (VERIFICADAS, endpoints activos)

**Frontend:**
- ✅ T-FE-06: Observer: Lista de Actas (referencia para patrón tabla/filtros)
- ✅ ODD #505: Design System Completion (Toast, tokens, A11y)
- ✅ T-FE-20: E2E setup (Playwright fixtures, auth mocking)

**No bloquea:** Puede hacerse en paralelo con T-FE-10

---

## 3. Criticidad

🟡 **MEDIA**
- Backend totalmente listo (no hay bloqueador infra)
- Frontend necesaria para MVP (users necesitan cargar evidencias)
- No bloquea cierre electoral (T-FE-10 es crítica), pero mejora UX observer

---

## 4. Estimación

**Tareas A-E: 12-16 horas**

- A. Viewer component: 4-5h
- B. Uploader component: 3-4h
- C. Service + API: 2h
- D. Page layout: 2-3h
- E. Tests + E2E: 1-2h

---

## 5. Compuertas

```bash
# Frontend
pnpm lint → 0 warnings
pnpm test → >70% coverage (evidencias-*)
pnpm build → success
npx playwright test → E2E PASS (upload + view flows)
```

---

## 6. QA Visual

**Si toca UI (SÍ):**
- Invocar `/ux-designer`: accesibilidad (44px targets, ARIA, keyboard)
- Invocar `/taste-skill`: branding (tokens, dark mode, transiciones)
- Documentar en apply-progress.md sección "QA Visual"

---

## 7. Notas Especiales

1. **Roles y acceso:**
   - Observer: ve solo sus propias fotos (filtrado backend)
   - Coordinator/Master/Admin: ven todas las fotos

2. **Estados de foto:**
   - PENDIENTE: subida, esperando review
   - APROBADA: coordinator aprobó
   - RECHAZADA: coordinator rechazó (sin observaciones)
   - CON_OBSERVACIONES: coordinator pide cambios

3. **Endpoint alternate (contingency):**
   - Observer normalmente usa POST /observer/actas/{id}/imagen
   - Si Coordinator revisa acta, puede subir foto también (POST /coordinator/actas/{id}/imagen)

4. **Download-URL:**
   - Backend genera signed URL temporal (Cloudflare R2)
   - Frontend redirige sin guardar URL en logs (seguridad)

---

## 8. Próximos Pasos

1. `/02-build-odd odd/tasks/odd-506-evidencias-upload-viewer.md` → implementar (12-16h)
2. `/03-verify-odd odd/tasks/odd-506-evidencias-upload-viewer.md` → validar
3. Si PASS: merge a develop
4. Si REWORK: Builder recibe fixes-required.md

---

## Checklist Final (Builder)

- [ ] Componente viewer renderiza lista + filtros
- [ ] Componente uploader carga archivo + progreso
- [ ] Service integra 5 endpoints backend
- [ ] Page routing `/evidencias` y `/evidencias/subir`
- [ ] Unit tests >70%
- [ ] E2E upload + view flows PASS
- [ ] `/ux-designer` audit OK
- [ ] `/taste-skill` audit OK
- [ ] apply-progress.md con números finales
- [ ] Ready para `/03-verify-odd`
