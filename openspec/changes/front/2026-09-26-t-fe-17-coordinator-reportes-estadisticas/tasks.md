# Tasks — T-FE-17: Centro de Reportes y Estadísticas de Coordinación (COORDINATOR)

Implementación de pantalla centralizada de métricas y descarga de expedientes para COORDINATOR, según design.md (D1-D3) y proposal.md.

**Estándares aplicados**: 02-builder.md §Estándares de Calidad Obligatorios
- Pruebas: 70% cobertura mínima, camino feliz + errores + límites + concurrencia
- Código: ESLint, Prettier, code smells (métodos < 30 líneas, complejidad < 10)
- UX/UI: Accesibilidad WCAG, responsividad, tema
- Errores: try-catch + contexto, logueo con detalles, nunca silenciar excepciones
- OWASP: Auth bypass, XSS, CSRF, sensitive data
- Documentación: Por qué (commits), tests comentados si no obvios, compensaciones en design.md

---

## Fase A: Servicio de Coordinación y Rutas

- [ ] **A.1** — En `frontend/app/src/app/features/coordinator/services/coordinator.service.ts`:
  - Implementar método `descargarExpedientePdf(actaId: number): Observable<Blob>` contra `GET /api/v1/coordinator/actas/{id}/expediente-pdf` con `responseType: 'blob'`.
  - **Validaciones**:
    - Verificar que `actaId` es número > 0 (rechazar nulos, strings, negativos)
    - Loguear inicio de descarga: `logger.info('Descargando expediente para acta', { actaId, usuario: this.authService.usuarioActual() })`
    - Manejo de errores HTTP (401 Unauthorized, 403 Forbidden, 404 Not Found, 500):
      ```typescript
      error: (err) => {
        if (err.status === 401) {
          this.error.set('Sesión expirada. Vuelve a autenticarte.');
        } else if (err.status === 403) {
          this.error.set('No tienes permiso para descargar este expediente.');
        } else if (err.status === 404) {
          this.error.set('Acta no encontrada.');
        } else {
          this.error.set('Error al descargar expediente. Intenta nuevamente.');
        }
        logger.error('Download failed for acta', { actaId, status: err.status, message: err.message });
      }
      ```

- [ ] **A.2** — En `frontend/app/src/app/app.routes.ts`:
  - Registrar ruta `coordinator/reportes` con `canActivate: [roleGuard]`, `data: { roles: ['COORDINATOR', 'MASTER', 'ADMIN'] }` (cumple OWASP auth bypass).
  - Ruta lazy-loaded: `loadComponent: () => import('./features/coordinator/pages/reportes/coordinator-reportes.page').then(m => m.CoordinatorReportesPage)`
  - Agregar ítem en navegación de coordinación en `SidebarComponent` (si existe) o menu de COORDINATOR.

---

## Fase B: Componente CoordinatorReportesPage

- [ ] **B.1** — Crear `CoordinatorReportesPage` en `frontend/app/src/app/features/coordinator/pages/reportes/`:
  
  **Estructura (B.1.a)**:
  - Banner superior con:
    - Porcentaje global de avance (ej. 219/322 JRVs, 67.9%)
    - Tasa de velocidad: actas procesadas por hora (calculado desde `fechaRegistro`)
    - Estado de conexión Supabase Realtime ("En vivo", "Desconectado")
  - Grid de tres columnas (responsive, stack en mobile):
    - Tarjeta **Progreso por Cantón**: barras apiladas % Santa Elena / La Libertad / Salinas
    - Tarjeta **Estados de Actas**: dots de color + porcentajes (ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA)
    - Tarjeta **Progreso por Cargo**: barras Prefecto / Alcalde / Concejales / CPCCS
  - Sección inferior:
    - Tabla de actas con columnas: ID, Estado, Cantón, Cargo, Novedad/Disputa, Acciones (Descargar Expediente PDF)
    - Métricas: Resoluciones del día (count), Tiempo promedio de subsanación (horas)
  
  **Lógica (B.1.b)**:
  - Usar signals para: `loading`, `error`, `metrics`, `actas`, `lastRefresh`
  - `ngOnInit()`: 
    - Cargar métricas en paralelo con `forkJoin()`: `dashboardService.getResumen()` + `dashboardService.getProgreso()` + `coordinatorService.getActasDisputa()`
    - Si conectado a Supabase Realtime, suscribirse a cambios de actas (on INSERT/UPDATE) y actualizar métricas sin reload
  - Métodos:
    - `cargarMetricas()`: carga inicial + retry en error
    - `calcularTasaPorHora()`: deriving from `fechaRegistro` timestamps en la jornada actual
    - `obtenerColorEstado(estado)`: mapa estado → color (ENVIADA: gris, EN_DISPUTA: rojo, CORREGIDA: amarillo, APROBADA: verde, ANULADA: negro)
  - **Manejo de errores**: 
    - Si falla carga de métricas: mostrar `ErrorStateComponent` con botón retry
    - Logging: `logger.error('Carga de métricas fallida', { error, usuario: authService.usuarioActual() })`
  
  **Accesibilidad (B.1.c)**:
  - `role="region"` en cada tarjeta de métrica
  - `aria-label` en botones de descarga: `aria-label="Descargar expediente de acta ${actaId}"`
  - Colores + símbolos (no solo color para estados)
  - Responsive: grid → flex en mobile (max-width: 768px)
  
  **Tema (B.1.d)**:
  - 100% theme tokens: `--color-text-primary`, `--color-border-subtle`, `--primitive-green-800`, colores de estado
  - Sin raw colors (#hex, rgba)
  - Breakpoints: mobile (≤640px), tablet (≤768px), desktop (>768px)

- [ ] **B.2** — Lógica de descarga de archivo:
  - Método `descargarExpediente(actaId: number)` en componente:
    ```typescript
    descargarExpediente(actaId: number): void {
      this.loading.set(true);
      this.coordinatorService.descargarExpedientePdf(actaId).subscribe({
        next: (blob: Blob) => {
          // Validar MIME type
          if (!['application/pdf', 'application/octet-stream'].includes(blob.type)) {
            this.error.set('Archivo descargado no es PDF válido.');
            logger.warn('Invalid MIME type received', { actaId, type: blob.type });
            this.loading.set(false);
            return;
          }
          // Crear Object URL temporal
          const url = window.URL.createObjectURL(blob);
          // Simular clic en <a> para descarga
          const link = document.createElement('a');
          link.href = url;
          link.download = `expediente-cne-acta-${actaId}.pdf`;
          link.click();
          // CRITICAL: Revocar URL para liberar memoria
          window.URL.revokeObjectURL(url);
          logger.info('Expediente descargado exitosamente', { actaId });
          this.loading.set(false);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set('Error al descargar expediente. Intenta nuevamente.');
          logger.error('Download failed', { actaId, error: err.message });
        }
      });
    }
    ```
  - **Prevención de memory leak**: SIEMPRE llamar `URL.revokeObjectURL(url)` después de disparar descarga
  - **Validación de blob**: Verificar MIME type antes de descargar (evitar XSS si backend retorna HTML)

---

## Fase C: Tests Unitarios y Verificación de Mutaciones

**Métricas esperadas**:
- **Mínimo 8 tests** (happy path + errores + límites + concurrencia)
- **Cobertura ≥ 70%** (líneas) en componente y servicio
- **0 code smells**: métodos < 30 líneas, complejidad ciclomática < 10

- [ ] **C.1** — En `coordinator-reportes.page.spec.ts` (crear si no existe):

  **C.1.a — Setup y mock data**:
  ```typescript
  const mockMetrics = {
    jrvsTotal: 322,
    jrvsProcessed: 219,
    actasPorHora: 15.5,
    estados: {
      ENVIADA: 45,
      EN_DISPUTA: 8,
      CORREGIDA: 12,
      APROBADA: 154,
      ANULADA: 0
    },
    progresoCancel: { 'Santa Elena': 70, 'La Libertad': 65, 'Salinas': 60 }
  };
  const mockActasDisputa = [
    { id: 1, actaNumber: 'A-001', canton: 'Santa Elena', estado: 'EN_DISPUTA' },
    { id: 2, actaNumber: 'A-002', canton: 'La Libertad', estado: 'EN_DISPUTA' }
  ];
  ```

  **C.1.b — Happy Path Tests** (3 tests mínimo):
  - ✅ `should initialize with loading state true` — Verificar que al crear el componente, `loading()` es `true`
  - ✅ `should load metrics on ngOnInit and display dashboard` — Cargar métricas con `forkJoin`, verificar que tablas y tarjetas se rellenan correctamente
  - ✅ `should calculate actas per hour correctly from timestamps` — Validar cálculo de `calcularTasaPorHora()` basado en `fechaRegistro`

  **C.1.c — Error Path Tests** (2 tests mínimo):
  - ✅ `should show error state when metrics fail to load` — Si `dashboardService.getResumen()` falla (HTTP 500), mostrar `ErrorStateComponent` y loguear
  - ✅ `should handle 401 Unauthorized during download and clear session` — Si descarga retorna 401, mostrar "Sesión expirada" y refrescar

  **C.1.d — Límites y Validación Tests** (2 tests mínimo):
  - ✅ `should reject invalid actaId (negative, zero, null) before download attempt` — Validar que `descargarExpediente(-1)` no hace llamada HTTP
  - ✅ `should validate MIME type of downloaded blob and reject non-PDF` — Si blob.type !== 'application/pdf', mostrar error sin descargar

  **C.1.e — Memory & Cleanup Tests** (2 tests mínimo):
  - ✅ `should revoke Object URL after download to prevent memory leak` — Verificar que `URL.revokeObjectURL()` fue llamado
  - ✅ `should unsubscribe from realtime and cleanup on ngOnDestroy` — Si hay suscripción Supabase, debe completarse en `ngOnDestroy` (con `takeUntil(this.destroy$)`)

  **Total mínimo: 9 tests (3 happy + 2 error + 2 límites + 2 cleanup)**

  **Comandos de ejecución**:
  ```bash
  cd frontend/app
  # Tests específicos
  pnpm test:ci -- coordinator-reportes.page.spec.ts --run
  
  # Cobertura
  pnpm test:coverage -- coordinator-reportes.page.spec.ts
  # Verificar ≥ 70% en src/app/features/coordinator/pages/reportes/ y src/app/features/coordinator/services/
  ```

- [ ] **C.2** — Verificación por mutación (ejecutada por quien implementa):
  - **Mutación M1**: Cambiar `blob.type !== 'application/pdf'` a `blob.type === 'application/pdf'` (invertir lógica de validación):
    - **Esperado**: Test `should validate MIME type of downloaded blob and reject non-PDF` **debe caer** (porque ahoraacepta no-PDF)
    - **Anotación en apply-progress.md**: `M1: Invertir validación MIME. Test que cayó: "should validate MIME type..."`
  
  - **Mutación M2**: Comentar o eliminar `window.URL.revokeObjectURL(url)`:
    - **Esperado**: Test `should revoke Object URL after download to prevent memory leak` **debe caer**
    - **Anotación en apply-progress.md**: `M2: Omitir revokeObjectURL. Test que cayó: "should revoke Object URL..."`

---

## Qué NO hacer en esta fase

- NO hacer llamadas de red sin token de autorización; usar siempre `AuthInterceptor` (inyectado automáticamente vía HttpClient)
- NO alterar datos de actas desde esta pantalla (es lectura-solo y descargas)
- NO acumular Object URLs en memoria: SIEMPRE revocar tras descarga
- NO loguear información sensible (tokens, contraseñas)
- NO usar `[innerHTML]` (riesgo XSS); usar `{{ variable }}` data binding
- NO omitir validación de tipos en métodos públicos
- NO crear métodos > 30 líneas sin refactorizar

---

## Compuertas antes de dar por terminada la fase

```bash
cd frontend/app

# Linting y formatting
pnpm lint
pnpm format:check

# Tests unitarios
pnpm test:ci --run

# Build en prod
pnpm build

# Cobertura (verificar ≥ 70% en CoordinatorReportesPage + CoordinatorService)
pnpm test:coverage

# Security (no high/critical vulnerabilities)
npm audit
```

**Criterios de paso**:
- ✅ `pnpm lint`: 0 errores
- ✅ `pnpm format:check`: código formateado
- ✅ `pnpm test:ci`: 9+ tests verdes, suites previas sin regresión
- ✅ `pnpm build`: exit 0 (sin warnings de tipo)
- ✅ Cobertura: ≥ 70% en componente y servicio
- ✅ `npm audit`: 0 high/critical
- ✅ Mutaciones M1 y M2: tests caen correctamente
