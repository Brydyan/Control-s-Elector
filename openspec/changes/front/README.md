# Frontend Changes — Spec-Driven Development (SDD)

Cambios frontend pendientes (nuevas funcionalidades), estructurados usando Spec-Driven Development.

## Cambios Pendientes

| Cambio | Título | Dependencias | Status |
|--------|--------|--------------|--------|
| T-FE-01 | Arquitectura Angular Base (feature-based, standalone) | ninguna | Pending |
| T-FE-02 | Routing con RBAC Guards | T-FE-01 | ✓ Archived (2026-09-27) |
| T-FE-03 | AuthService y Manejo de Tokens | T-FE-01 | Pending |
| T-FE-04 | Interceptors HTTP (JWT + Refresh) | T-FE-03 | Pending |
| T-FE-05 | Pantalla de Login | T-FE-02, T-FE-03, T-FE-04 | Pending |
| T-FE-06 | Observer: Lista de Actas Propias | T-FE-02, T-FE-03, T-FE-04, T-FE-05 | Pending |
| T-FE-07 | Formulario de Registro de Acta | T-FE-06 | Pending |
| T-FE-08 | Captura y Compresión de Imagen (WebP) | T-FE-01 | Pending |
| T-FE-09 | Resiliencia Offline con IndexedDB | T-FE-07, T-FE-08 | Pending |
| T-FE-10 | Coordinator: Gestión Global de Actas | T-FE-02, T-FE-03 | Pending |
| T-FE-11 | Dashboard en Tiempo Real (Supabase Realtime) | T-FE-05 | Pending |
| T-FE-12 | Componentes Compartidos (Shared) | T-FE-01 | Pending |
| T-FE-13 | Modo "Foto Primero" y Cotas Electorales | T-FE-07, T-FE-08 | Pending |
| T-FE-14 | Observer: Corrección de Acta en Disputa | T-FE-06, T-FE-07 | Pending |
| T-FE-15 | Dashboard Analítico: Resultados y Progreso Cantonal | T-FE-11 | Pending |
| T-FE-16 | MASTER: Configuración Preelectoral (Cargos, Partidos, Candidatos) | T-FE-02 | Pending |
| T-FE-17 | Coordinator: Reportes y Expedientes CNE | T-FE-10, T-BE-20 | Pending |
| T-FE-18 | Menús Dinámicos RBAC y Navegación Limpia en Sidebar | T-FE-02, T-FE-03 | Pending |

**Línea base 2026-09-07:** Proyecto Angular inicializado. Todas las tareas son nuevas (sin retrospectivas).

## Estructura de cada cambio

```
2026-09-07-t-fe-NN-descripcion-slug/
  ├── proposal.md         ← Intent, scope, criterios de aceptación
  ├── design.md           ← Decisiones arquitectónicas D1, D2, ... con alternativas descartadas
  ├── specs/
  │   └── frontend/
  │       └── spec.md     ← Escenarios GIVEN/WHEN/THEN + mutaciones
  └── tasks.md            ← Fases A/B/C, checklist ejecutable, gates, "no hacer"
```

## Dependencia Graph

```
T-FE-01 (Arquitectura)
  ├─→ T-FE-02 (Routing)
  │    ├─→ T-FE-05 (Login)
  │    └─→ T-FE-10 (Coordinator Dashboard)
  ├─→ T-FE-03 (AuthService)
  │    ├─→ T-FE-04 (Interceptors)
  │    ├─→ T-FE-05 (Login)
  │    └─→ T-FE-06 (Observer List)
  │         ├─→ T-FE-07 (Acta Form)
  │         │    ├─→ T-FE-08 (Image Compression)
  │         │    └─→ T-FE-09 (Offline IndexedDB)
  │         └─→ T-FE-10 (Coordinator Dashboard)
  └─→ T-FE-12 (Shared Components)
       └─→ Usados en T-FE-05, T-FE-06, T-FE-10, T-FE-11

T-FE-05 (Login)
  └─→ T-FE-11 (Realtime Dashboard)
```

## Próximos Pasos

1. **Orden de implementación (respeta dependencias):**
   - T-FE-01: Arquitectura
   - T-FE-02: Routing
   - T-FE-03: AuthService
   - T-FE-04: Interceptors
   - T-FE-05: Login
   - T-FE-12: Shared Components (reutilizable)
   - T-FE-06: Observer List
   - T-FE-07: Acta Form
   - T-FE-08: Image Compression
   - T-FE-09: Offline
   - T-FE-10: Coordinator Dashboard
   - T-FE-11: Realtime Dashboard

2. **Para cada cambio:**
   - Leer proposal.md (intent)
   - Revisar design.md (decisiones)
   - Ejecutar tasks.md (Fase A/B/C)
   - Ejecutar spec.md (escenarios + mutaciones)
   - Verificar compuertas (gates)

3. **Al cerrar:**
   - Mover a `openspec/changes/archive/front/` con archive-report.md
   - Fusionar specs delta en `openspec/specs/frontend/spec.md`
   - Actualizar `openspec/ROADMAP.md`

## Decisiones Arquitectónicas Clave

| Cambio | D1 | D2 | D3 |
|--------|----|----|-----|
| T-FE-01 | Standalone 100% | tsconfig paths | Barrels |
| T-FE-02 | Guards funcionales | authGuard + roleGuard composables | Lazy-load |
| T-FE-03 | Token en memoria | BehaviorSubject | AuthService orquesta |
| T-FE-04 | HttpInterceptorFn | Jwt + Refresh stacked | 401 → retry |
| T-FE-05 | features/auth/login | Validación realtime | Redirect por rol |
| T-FE-06 | Filtro frontend | GET /api/observer/mis-actas | Tabla filtrable |
| T-FE-07 | FormArray candidatos | sumaValidator | POST acta |
| T-FE-08 | Canvas WebP 0.82 | Recomprimir 0.65 si >500KB | Observable Blob |
| T-FE-09 | IndexedDB + polling | Retry 30s automático | Fallback FS |
| T-FE-10 | Filtro frontend | AlertController confirm | PUT actions |
| T-FE-11 | Supabase Realtime WS | ng2-charts | Pie + Bar |
| T-FE-12 | String union types | IonModal confirm | Barrel shared |
| T-FE-18 | NAV_CONFIG declarativo | Erradicación placeholders | Resiliencia offline sin mapRoute |

## Trampas Documentadas

Ninguna aún. Se agregarán conforme aparezcan en implementación.
