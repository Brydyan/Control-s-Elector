# ROADMAP — SDD en Control Electoral

Punto de entrada para una sesión nueva. Si abrís este repo con un agente, esto es lo primero
que lee. Contiene **decisiones ya cerradas** — no se vuelven a discutir — y las **trampas
medidas** de este repositorio.

Método: `SDD-PLAYBOOK.md` en la raíz. Este archivo es su adaptación concreta a Control Electoral.

---

## 1. Decisiones cerradas

| Decisión | Valor | Cerrada el |
|---|---|---|
| Sitio de artefactos | `openspec/changes/{back,front,infra}/<fecha>-<slug>/`, archivado **plano** en `openspec/changes/archive/` | 2026-09-07 |
| Backend de persistencia | **hybrid** — archivos en `openspec/` + espejo en engram | 2026-09-07 |
| Implementador | subagente `sdd-apply` | 2026-09-07 |
| Verificador | subagente aparte, **contexto limpio**, nunca el mismo que implementó | 2026-09-07 |
| Fuente de trabajo | `documents/task-control/{backend,frontend,infra}.md` + `*task/T-*.md` | 2026-09-07 |
| Idioma de artefactos | español; identificadores y estados en el idioma del código | 2026-09-07 |
| Canal de distribución y runtime | **PWA instalable (Angular 19 + Ionic 8 + Service Worker + Webmanifest)** como canal principal; **Capacitor APK** como contingencia secundaria; `navigator.storage.persist()` para blindar IndexedDB contra evicciones del navegador | 2026-10-03 |
| Selección de JRVs por Veedor | **Libre selección** (sin pre-asignación; tabla `observer_jrv_asignacion` deprecada desde 2026-08-14). RLS aísla actas por `registrada_por_id` | 2026-08-14 |

**No se discute de nuevo:** una sola app con RBAC por ruta (sin subdominios por rol), UUID
público `publicId` en DTOs, `AuditLogService → JPA → PG` siempre. Ver `CLAUDE.md`.

---

## 2. El reparto de roles en este proyecto

Tres papeles. **Ninguno se verifica a sí mismo.**

| Papel | Quién | Qué hace aquí | Qué NO hace |
|---|---|---|---|
| **Arquitecto / QA** | agente principal (Opus) | `proposal.md`, `design.md`, `specs/`, `tasks.md`. Decide si un change cierra. Mantiene este ROADMAP | No escribe el código de la funcionalidad |
| **Implementador** | subagente `sdd-apply` (Sonnet) | Código + tests siguiendo `tasks.md`. Ejecuta sus propias mutaciones y anota **el nombre del test que cayó** en `apply-progress.md` | No decide si su trabajo está bien. No archiva. No commitea |
| **Verificador** | subagente nuevo, contexto limpio (Sonnet) | Ejecuta compuertas, **re-corre las mutaciones por su cuenta**, escribe `verify-report.md` y `fixes-required.md` | No arregla lo que encuentra. No archiva |

El Verificador **nunca** ve la conversación donde se decidió la implementación: hereda las
suposiciones que hay que poner a prueba. Se lanza siempre nuevo, con un prompt autocontenido.

Cada papel tiene su archivo, con su plantilla de lanzamiento y sus límites:

| Papel | Archivo |
|---|---|
| índice, bloques que van pegados en todo prompt, qué no delegar | `documents/agents/README.md` |
| Arquitecto / QA | `documents/agents/01-arquitecto-qa.md` |
| Builder (Implementador) | `documents/agents/02-builder.md` |
| Verify (Verificador) | `documents/agents/03-verify.md` |

---

## 3. Compuertas y línea base

Medidas el **2026-11-07**, tras verificación ODD #478 (CORS + License + JaCoCo):

```
backend build     ./mvnw -B clean verify                     BUILD SUCCESS
backend test      mvn -B test                                11 suites / 54 tests
                                                             0 failure / 2 error (Testcontainers/Docker)
backend coverage  JaCoCo gate (piso 0.08, real 0.1175)       PASS ✅
backend format    spotless:check                             0 violaciones (exit 0) ✅
frontend lint     pnpm lint                                  0 errores (exit 0) ✅
frontend format   pnpm format:check                          0 errores ✅
frontend build    pnpm build                                 bundle generado en www (exit 0) ✅
frontend test     pnpm test:ci                               68 suites / 651 tests / 0 failures (exit 0) ✅
```

**Cambios respecto 2026-11-05:**
- Backend tests +7 (CorsPreflightIT nueva)
- Frontend tests +23 (T-FE-20 completada)
- JaCoCo integration operacional (CI artifact publish)

Working directory obligatorio: backend en `backend/electoral-api-quarkus/`, frontend en `frontend/app/`.

**Nota:** Frontend tests crecieron significativamente: 241 (2026-10-04) → 628 (2026-11-05) tras T-FE-19 (admin CRUD) y T-FE-20 (image compression). Cada cambio nuevo suma tests en su área.

**Los números importan.** Si al añadir tests el total no sube, los tests nuevos no se están
ejecutando. Eso es un fallo de la fase, no un detalle.

**La suite de backend está 100% en VERDE**: el blocker inicial `78 ERROR` fue resuelto y archivado
en `openspec/changes/archive/2026-09-24-fix-liquibase-rbac-order/`. T-BE-13 (Dashboard) también
archivado. Los tests nuevos se distinguen y se miden con rigor contra esta línea base.

**La suite de frontend está 100% en VERDE con `isolate: true`**: La carrera asíncrona de teardown
entre Vitest 5 y `@ionic/core` fue resuelta aislando `AppComponent` con `template: '<div></div>'` y
`try-catch` en `addIcons`. `continue-on-error: true` fue erradicado de CI y archivado en
`openspec/changes/archive/2026-09-25-fe-vitest-ionic-cleanup-race/`.

**T-FE-12 (Componentes Compartidos) archivado y consolidado**: Los componentes reutilizables
(`EstadoBadge`, `RolBadge`, `ConfirmDialog`, `NotificacionBell`, `Paginator`, suite `shared/ui/`)
y los barrels `@shared/*` se integraron con 241 tests pasando en verde (exit 0), mutaciones M2 y M3
verificadas en vivo y 0 errores de compilación (`NG8002`, `TS2322`).


---

## 4. Trampas de este repositorio

Descubiertas midiendo, el 2026-09-07. Cada una cuesta una ronda si no se avisa. Pasalas en
**todos** los prompts de implementación y verificación.

| Trampa | Qué pasa | Qué hacer |
|---|---|---|
| `./mvnw test \| tail` | El exit code es el de `tail`, no el de Maven. Da **exit 0 con 78 tests en error** | Redirigir a archivo y leer los XML de `target/surefire-reports/`. Nunca juzgar por el exit de un pipe |
| CI corre `-DskipTests` | `.github/workflows/ci.yml` hace `./mvnw clean package -DskipTests`. La compuerta de merge **ejecuta cero tests** | Ver §4.6 del playbook. Es parte del primer change |
| BD local ≠ Testcontainers | Las migraciones fallan solo en BD limpia. En la BD de dev ya migrada no se ve nada | Verificar siempre contra contenedor nuevo, nunca contra la BD local |
| `ng test` cuelga | `karma.conf.js:40-41` → `browsers:['Chrome']`, `singleRun:false` | `ng test --watch=false --browsers=ChromeHeadless` |
| El shell del proyecto es **fish** | `$?` no existe; es `$status`. `eslint.config*` sin match aborta el comando | Escribir comandos sin globs opcionales, o correrlos con `bash -c` |
| `mvnw` no está en la raíz | Está en `backend/electoral-api/mvnw` | Fijar el working directory siempre |
| MapStruct sin `componentModel="spring"` | Convención del proyecto: mappers como `@Bean` en `MapperConfig.java` | Ver `CLAUDE.md`, sección Convenciones de Código |
| Android LMK mata la pestaña al abrir cámara en móviles de gama baja (2GB RAM) | Android Low Memory Killer elimina el proceso Chrome/PWA al invocar la cámara nativa si el heap o los buffers de imagen saturan la RAM | Patrón "Guardar antes de capturar" en IndexedDB (`acta_drafts`), decodificación C++/GPU vía `createImageBitmap(blob, { resizeWidth: 1920 })`, cero Base64 en memoria y compresión WebP en Web Worker |
| El comentario que miente / pre-asignación deprecada | Código antiguo o comentarios mencionan que el veedor solo ve sus JRVs asignadas vía `observer_jrv_asignacion` | No reintroducir filtros ni joins con `observer_jrv_asignacion`. Fue retirado deliberadamente el 2026-08-14 para permitir libre selección de JRVs por contingencias en campo; RLS filtra por `registrada_por_id` |
| Vitest 5.0 + `@ionic/core` Stencil Custom Elements en cleanup | Al montar `<ion-app>` o `<ion-router-outlet>` en tests unitarios (`AppComponent`), Stencil dispara imports dinámicos en `connectedCallback()`. Al cerrar el runner de Vitest con `isolate: true`, la microtarea rezagada causa `Unhandled Rejection: Vite module runner has been closed` y `exit 1` | En `app.component.spec.ts` sobreescribir la plantilla con `template: '<div></div>'` en `TestBed.overrideComponent` para neutralizar el montaje de `<ion-router-outlet>`, y proteger `addIcons` en `src/test-setup.ts` con `try-catch`. NUNCA usar `continue-on-error: true` en CI |

### Cómo buscar trampas nuevas

```bash
./mvnw -B test > /tmp/test.log 2>&1; echo "exit=$?"   # bash: nunca en un pipe
npx tsc -p tsconfig.app.json --noEmit --listFiles | wc -l   # si da 0, no compila nada
grep -n "skipTests\|continue-on-error" .github/workflows/*.yml
```

---

## 5. Cola de trabajo

Fuente de verdad: `documents/task-control/`. Un change SDD por tarea `T-*`, salvo que
dos tareas compartan una frontera (entonces van juntas — §4.1 del playbook).

Cada área tiene su índice, con sus compuertas, su cola y lo que hay que verificar en ella:

| Área | Índice | Fuente | Código |
|---|---|---|---|
| back | `openspec/changes/back/README.md` | `documents/task-control/backend.md` | `backend/electoral-api/` |
| front | `openspec/changes/front/README.md` | `documents/task-control/frontend.md` | `frontend/app/` |
| infra | `openspec/changes/infra/README.md` | `documents/task-control/infra.md` | `compose.yaml`, `.github/workflows/` |

Convención de carpetas y archivado: `openspec/changes/README.md`.

Lo que sigue es el resumen; el detalle vive en cada índice.

### Ruta Maestra de Alineación — Elecciones Seccionales 2027

Para garantizar que el desarrollo no se desvíe de los requerimientos operativos de campo, el trabajo se ejecuta en **tres fases estrictamente secuenciales**:

#### Fase 1 · Backend Core y Cierre de Ciclo de Vida (Prioridad Crítica)
1. **`T-BE-14` (Storage en actas)**: Archivado / Completado (S3 con compensación transaccional).
2. **`T-BE-16` (Observaciones en Evidencia)**: Archivado / Completado (Entidad ObservacionEvidencia y notas predefinidas en fotos de evidencia).
3. **`T-BE-15` (Tests de integración E2E)**: Archivado / Completado (Tests de ciclo de vida completo).
4. **`T-BE-17` (Aislamiento RLS en MockMvc)**: Archivado / Completado.
5. **`T-BE-18` (Subida de Evidencia por Coordinador)**: `openspec/changes/back/2026-09-25-t-be-18-coordinator-evidencia-upload/`. Endpoint `POST /api/v1/coordinator/actas/{id}/imagen` para adjuntar foto física recibida por contingencia cuando el veedor queda sin batería o con teléfono dañado.
6. **`T-BE-19` (Incidencias Operativas de JRV sin Acta)**: `openspec/changes/back/2026-09-25-t-be-19-incidencias-jrv/`. Tabla `incidencia_jrv` y endpoints de reporte para alertar mesas no instaladas, impedimentos a veedores o disturbios sin necesidad de un acta de escrutinio.
7. **`T-BE-20` (Expediente de Impugnación CNE en PDF)**: `openspec/changes/back/2026-09-25-t-be-20-dossier-impugnacion-cne-pdf/`. Generador OpenPDF que compila en 1 página formal la foto del acta, el cuadro de votos, la inconsistencia detectada y sello forense para defensa legal en audiencia pública CNE.
8. **`T-BE-21` (Acta Detalle Completo DTO & GET Observer)**: `openspec/changes/back/2026-09-26-t-be-21-acta-detalle-dto-observer-get/`. DTO enriquecido `ActaDetalleCompletoDto` con votos individuales de candidatos, URL de evidencia física prefirmada y motivo de disputa, más endpoint `GET /api/v1/observer/actas/{id}`.

#### Fase 2 · Frontend Estabilización y Operación de Campo (Spec 2027)
1. **`2026-09-25-fe-vitest-ionic-cleanup-race`**: Archivado / Completado (Aislamiento de teardown en `AppComponent` con `template: '<div></div>'`, `isolate: true` limpio en Vitest 42/42 suites 240/240 tests y compuerta dura en CI sin `continue-on-error: true`).
2. **`T-FE-12` (Componentes Compartidos)**: Archivado / Completado (`openspec/changes/archive/2026-10-04-t-fe-12-shared-components/`). Componentes atómicos UI, barrels `@shared/components` y `@shared/ui`, modal `ConfirmDialog` con `FocusTrapDirective`, mutaciones reactivas M2 y M3 verificadas en vivo, 241 tests pasando (exit 0). Conciliación retrospectiva de `T-FE-01` a `T-FE-07` pendiente.
3. **`T-FE-13` (Modo "Foto Primero" y Cotas Electorales)**: `openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/`. Reordenar wizard: 1: Foto -> 2: Votos -> 3: Revisión (salvaguarda ante desalojo militar); warning > 350 sufragantes, tope duro 500; cuadre estricto de votos 1:1 (actas unívocas por cargo/sub-lista CPCCS); persistencia de borrador local en IndexedDB (`acta_drafts`) previa a captura para resistir cierres del navegador/LMK.
4. **`T-FE-08` (Compresión WebP y Optimización de RAM)**: Decodificación directa C++/GPU vía `createImageBitmap(blob, { resizeWidth: 1920 })`, compresión WebP en Web Worker con `OffscreenCanvas`, cero Base64 (blobs binarios puros), limpieza inmediata con `URL.revokeObjectURL()` y captura PWA con `<input type="file" accept="image/*" capture="environment">`.
5. **`T-FE-09` (Offline-First PWA, IndexedDB Blindada & PIN de 4 dígitos)**: Cola en IndexedDB blindada con `navigator.storage.persist()`, patrón "Guardar antes de capturar" en almacén `acta_drafts` con auto-recuperación ante reinicio de pestaña por Android LMK, Service Worker para app shell 100% offline, reintento 30s y **desbloqueo rápido por PIN offline** cuando el JWT expira sin cobertura.
6. **`T-FE-10` (Coordinador Split-View)**: Vista 50/50 desktop (datos + visor WebP con zoom/rotación) + acción `Disputar Acta con motivo (PUT /api/v1/coordinator/actas/{id}/disputar)` + botón de contingencia WhatsApp `wa.me/` + descarga de expediente PDF CNE.
7. **`T-FE-11` (Dashboard Reactivo)**: Backend Authority en agregaciones de PostgreSQL con Supabase Realtime como gatillo debounced (500ms).
8. **`T-FE-14` (Observer Corrección de Acta Disputada)**: `openspec/changes/front/2026-09-26-t-fe-14-observer-correccion-acta/`. Pantalla móvil `/observer/acta/:id/corregir` con motivo de coordinador, visor de foto original, checksum dinámico y reenvío transaccional.
9. **`T-FE-15` (Dashboard Analítico: Resultados por Cargo y Progreso Cantonal)**: `openspec/changes/front/2026-09-26-t-fe-15-dashboard-resultados-progreso/`. Vistas `/dashboard/resultados` y `/dashboard/progreso` con tabs de dignidades, líder provisional, barras de porcentajes y avance cantonal de Santa Elena.
10. **`T-FE-16` (Módulo Configuración Preelectoral MASTER)**: `openspec/changes/front/2026-09-26-t-fe-16-master-configuracion-electoral/`. Vista unificada `/master/configuracion` con tabs `Cargos`, `Partidos Políticos` y `Candidatos`, modales de alta/edición y CRUD con la API backend.
11. **`T-FE-17` (Centro de Reportes y Expedientes CNE para Coordinador)**: `openspec/changes/front/2026-09-26-t-fe-17-coordinator-reportes-estadisticas/`. Vista `/coordinator/reportes` con panel de avance global, distribución de estados, métricas de resolución y descarga de expedientes de impugnación PDF.
12. **`T-FE-18` (Menús Dinámicos RBAC y Navegación Limpia en Sidebar)**: `openspec/changes/front/2026-10-03-t-fe-18-sidebar-rbac-dynamic-menus/`. Refactorización de `SidebarComponent` con configuración declarativa `NAV_CONFIG` por rol, erradicación de enlaces a `PlaceholderPage` (`/actas`, `/evidencias`, `/admin`), eliminación de `mapRoute()` procedimental y resiliencia offline ante fallos en `GET /api/v1/menus/me`.
13. **`T-FE-19` (Gestión de Usuarios MASTER — CRUD, Soft-Delete y Acciones con Íconos)**: `openspec/changes/front/2026-10-04-t-fe-19-admin-usuarios-crud-softdelete/`. Corrección de `esMaster` vía `AuthStore` (eliminación de lectura de `localStorage`), botón de alta con visualización de contraseña temporal Base64Url, columna de acciones basada en íconos (`eye-outline`, `create-outline`, `trash-outline`, `refresh-outline`) y soft-delete estricto con confirmación previa.

#### Fase 3 · Infraestructura, Realtime y Producción
1. **`T-IN-06` (Supabase Realtime)**: WebSockets para emisión y captura de eventos de cambio de estado en `acta` (refresco en vivo de tableros).
2. **`T-IN-11` (GitHub Actions CI/CD)**: Pipeline estricto sin `-DskipTests` ni `continue-on-error`, con despliegue automático en push a `main`.
3. **`T-IN-07` / `T-IN-09`**: Configuración de producción para `api.twintailcs.xyz` (Railway) y `control.twintailcs.xyz` (Vercel).

---

## 6. El ciclo, en concreto

```
documents/task-control/T-XX-NN.md
        │
        ▼
  Arquitecto ──► openspec/changes/<área>/<fecha>-<slug>/
                   proposal.md · design.md · specs/<dominio>/spec.md · tasks.md
        │
        ▼
  Implementador (sdd-apply) ──► código + tests + apply-progress.md
        │                        (con el nombre del test que cayó en cada mutación)
        ▼
  Verificador (contexto limpio) ──► verify-report.md · fixes-required.md
        │
        ├─ CRITICAL ─► vuelve al Implementador con fixes-required.md
        │
        ▼
  Arquitecto ──► archive-report.md · git mv a openspec/changes/archive/
                 y renombra la tarea a COMPLETADO-T-XX-NN.md
```

Los delta specs se fusionan en `openspec/specs/<dominio>/spec.md` al archivar.
Archivar es **`git mv`**, nunca copiar-y-borrar (§7 del playbook: pasó 4 veces).

---

## 7. Reglas de mutación

Cada garantía nueva lleva su mutación en `tasks.md`. Un test que pasa no prueba nada hasta
que lo viste fallar.

```bash
git status --short                    # limpio ANTES
cp ActaService.java ActaService.java.bak
#  … mutar, correr el test, anotar cuál cayó y por nombre …
cp ActaService.java.bak ActaService.java
rm ActaService.java.bak
git status --short                    # limpio DESPUÉS
```

**Nunca `git checkout --`** para restaurar: se lleva por delante cualquier cambio sin
commitear alrededor.

### Mutaciones canónicas de este dominio

Son las garantías que no se pueden romper (ver `CLAUDE.md`). Cada change que las toque
lleva su mutación:

| Garantía | Mutación | Debe caer |
|---|---|---|
| Unicidad `jrv_id + cargo_id` | quitar `findActaActivaConLock` de `ActaService.registrar` | el test de doble registro |
| `SUM(votos) + nulos + blancos = totalSufragantes` | cambiar `!=` por `==` en la validación | el test de suma inválida |
| Sólo MASTER anula | quitar `@PreAuthorize("hasRole('MASTER')")` de `CoordinatorController.anular` | el test de COORDINATOR → 403 |
| Sólo actas APROBADAS se anulan | quitar el guard de estado en `ActaService.anular` | el test de anular ENVIADA |
| `ADMIN` nunca escribe | quitar el guard de rol del endpoint de escritura | el test de ADMIN → 403 |
| Actas ANULADA fuera de estadísticas | quitar el filtro `!= ANULADA` | el test de dashboard |

Si no cae ninguno, el test es decorativo y hay que rehacerlo antes de seguir.

---

## 8. Patrones de defecto a cazar en este repo

Los seis del playbook (§4), instanciados aquí:

- **§4.1 nadie recorre el camino** — frontera Angular ↔ Spring: el DTO que el frontend
  envía a `POST /api/v1/observer/actas` ¿es un `ActaRequestDto` válido? Hoy nadie lo prueba.
- **§4.2 regla en un sitio y no en su vecino** — `ObserverController` usa
  `@PreAuthorize("@authz.has('acta:upload')")`; `CoordinatorController` usa
  `hasAnyRole(...)`. Dos mecanismos de autorización conviviendo: comprobar que cada endpoint
  aplica el que le toca.
- **§4.3 el comentario que miente** — `ActaService.registrar` líneas 77-82 afirman que RLS
  es el control vigente. Verificar que RLS **existe y está activo**, no el comentario.
- **§4.4 control que compara contra un nombre** — buscar `getRol().getNombre().equals("MASTER")`
  y equivalentes. Si alguien puede renombrar el rol, el control no existe. La tabla `rol`
  tiene `nombre` editable.
- **§4.5 depende de dónde corre** — ya materializado: migraciones que sólo fallan en BD limpia.
- **§4.6 compuerta sin garantía** — ya materializado: CI con `-DskipTests`.

---

## 9. Estado del ROADMAP

Última actualización: **2026-11-07**, por Verify (ODD Phase 0 completado).

ODD/SDD archivados recientemente:
- **`odd/archive/VERIFIED-2026-11-07-compliance-cors-license-jacoco.*`** (ODD Phase 0 completo: CORS preflight 7 tests + 6 mutations, LICENSE Apache 2.0, THIRD_PARTY_LICENSES.md generado, JaCoCo 0.8.13 con gate 0.08, PWA manifest + icons reparados. Verify Round 1: ✅ PASS.)
- `openspec/changes/archive/2026-09-25-fe-vitest-ionic-cleanup-race/` (Compuerta estricta CI y 240/240 tests pasando con `isolate: true`)
- `openspec/changes/archive/2026-10-04-t-fe-11-realtime-dashboard/`
- `openspec/changes/archive/2026-10-04-t-fe-12-shared-components/` (Componentes compartidos @shared/*, ConfirmDialog con focus trap, 241 tests, exit 0)
- `openspec/changes/archive/2026-11-04-t-fe-20-image-pipeline-compression/` (GPU acceleration con `createImageBitmap()`, Web Worker async, WebP doble pasada 0.80/0.65, 628 tests totales, M1/M2 mutaciones verificadas)

Changes activos / preparados:
- `openspec/changes/auth/2026-11-05-t-auth-inv-01-invitaciones/` (Email-based user invitations, JWT 24h, self-service activation)
- `openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/`
- `openspec/changes/front/2026-09-26-t-fe-{14..17}/`
- `openspec/changes/front/2026-10-03-t-fe-18-sidebar-rbac-dynamic-menus/`
- `openspec/changes/front/2026-10-04-t-fe-19-admin-usuarios-crud-softdelete/`

---

## 10. Priorización — Noviembre 2026 (ODD + SDD)

**Actualización:** 2026-11-07  
**Contexto:** Auditoría compliance + bugs dashboard master + feature invitaciones usuarios  
**Principio:** ODD (fixes rápidos) desbloquean SDD (features formales); criticidad por impacto en usuario

### Matriz de Priorización

| Orden | Ticket | Tipo | Pts | Criticidad | Bloqueador | Dependencia |
|-------|--------|------|-----|-----------|-----------|------------|
| **1** | #478 | ODD | 1 | 🔴 CRÍTICO | ✅ SÍ (frontend↔backend) | None |
| **2** | #490 | ODD | — | ✅ DONE | ✅ RESOLVED | bdfa452 |
| **3** | #482 | SDD | 13 | 🟡 ALTO | ❌ Legal (GDPR/RGPD) | #478 (CORS) |
| **4** | #492 | SDD | 5 | 🟡 ALTO | ❌ Feature (users) | None |
| **5** | #486 | SDD | 5 | 🟠 MEDIO | ❌ Feature (consent) | Product decision |
| **6** | #488 | SDD | 5 | 🟠 MEDIO | ❌ Monitoring | Sentry account setup |

**Total:** 29 story points (~4-5 sprints)

**⚠ UPDATE (2026-11-07):** ODD #490 RESOLVED UPSTREAM
- Commit bdfa452 (develop) already fixed Master dashboard routing issues
- /observer/mis-actas route added, /master/configuracion refactored
- Dashboard endpoints /resultados, /progreso canonical (no -detallado suffix)
- **Action:** Mark ODD #490 as DONE; keep ODD #478 only

### Ejecución Secuencial

#### **Phase 0: ODD (Bloqueadores — completado)**

```
ODD #478 (CORS + LICENSE + JACOCO)
├─ 55 min ejecutados (2026-11-07)
├─ Desbloquea: Todo (frontend/backend communication)
├─ Status: ✅ VERIFIED (PASS, no blockers)
├─ Archivado: odd/archive/VERIFIED-2026-11-07-compliance-cors-license-jacoco.md
└─ Verify report: CORS mutations 6/6 verified, gates all green

ODD #490 (Dashboard routing)
├─ Status: ✅ RESOLVED UPSTREAM (Commit bdfa452)
├─ Action: Superseded by develop merge
└─ No further action needed
```

**Completado:** ODD #478 ready to unblock Phase 1  
**Salida:** CORS configured ✅, LICENSE created ✅, Jacoco integrated ✅, PWA manifest fixed ✅

#### **Phase 1: SDD Sprint 1 (High Priority — Week 1-2)**

```
T-LEGAL-01 #482 (Privacy + Terms — GDPR/RGPD)
├─ 13 story points
├─ 6 tasks (DB, endpoints, pages, modals, tests)
├─ Prerequisite: #478 (CORS must be done)
├─ Contexto: PWA privada. Cumplimiento GDPR/RGPD: Art. 13/14 (info), 15 (export), 17 (delete)
├─ Salida: Privacy Policy + Terms + auditoría de aceptaciones
└─ Status: Ready for Builder (proposal + design complete, refocused for private PWA)

T-AUTH-INV-02 #492 (User Invitations + SMTP)
├─ 5 story points (4.5pt actual)
├─ 6 tasks (DB, service, endpoint, form, tests)
├─ Prerequisite: None (independent, can run in parallel with #482)
├─ Salida: MASTER can invite users via SMTP with 24h activation token
└─ Status: Ready for Builder (design complete)
```

**Estimación:** 18 story points, ~2-3 weeks  
**Parallelizable:** Yes (no dependencies between them, can both start immediately after #478)

#### **Phase 2: SDD Sprint 2 (Compliance — Week 3-4, conditional)**

```
T-COMPLIANCE-07 #486 (Consent Banner + Permissions)
├─ 5 story points
├─ Prerequisite: Product decision (geolocation? camera? notifications?)
├─ Status: Needs tasks.md completion
└─ Block if: Features not needed for MVP

T-COMPLIANCE-05 #488 (Sentry Error Tracking)
├─ 5 story points
├─ Prerequisite: Sentry SaaS account + API keys
├─ Status: Needs tasks.md completion
└─ Can start: Backend integration while frontend waiting for tasks.md
```

**Estimación:** 10 story points, ~2-3 weeks (conditional)  
**Decision Gate:** 2026-11-10 (product confirms scope)

### Árbol de Dependencias

```
┌─── ODD #478 (CORS)
│    └─► SDD #482 (Privacy+Terms) ✅
│
├─── ODD #490 (Dashboard) ✅
│
├─── SDD #492 (User Invitations) ✅
│    └─► Puede empezar en paralelo con #482
│
└─── SDD #486 (Consent) + #488 (Sentry)
     └─► Dependen de product decision (geolocation, notifications, errors)
```

### Criterios de Cierre

**ODD #478 closed when:**
- [ ] CORS config added to `application.properties`
- [ ] Frontend can call `GET /api/v1/dashboard/resultados` (200 OK)
- [ ] OPTIONS preflight returns 200 with CORS headers

**ODD #490 closed when:**
- [ ] Dashboard resultados + progreso load data (no errors)
- [ ] Mis Actas navigates + shows content
- [ ] Catálogos route exists + tabs work

**SDD #482 closed when:**
- [ ] Verify phase complete (mutations all pass)
- [ ] Privacy + Terms pages accessible
- [ ] Terms modal appears on login (version mismatch)
- [ ] Export data endpoint returns JSON
- [ ] Archive-report.md signed off

**SDD #492 closed when:**
- [ ] Verify phase complete (mutations all pass)
- [ ] MASTER can create users via form
- [ ] SMTP email sent with credentials
- [ ] Username auto-generated correctly
- [ ] Rate limiting enforced
- [ ] Archive-report.md signed off

### Trampas Nuevas (2026-11-07)

| Trampa | Qué pasa | Mitigación |
|--------|----------|----------|
| `environment.apiUrl` hardcoded | Frontend calls `http://localhost:8080` en prod | Verificar en environment.prod.ts antes de deploy |
| Rutas fantasma en navbar | Nav apunta a `/master/configuracion` que no existe | Grep todas las rutas en app.routes.ts vs NavbarComponent |
| SMTP sandbox vs prod | Mailtrap/mailhog en dev, Gmail SMTP en prod | Env vars para SMTP_HOST, SMTP_PORT, SMTP_FROM |
| Temp password en logs | Password temporario se registra en audit_log | Never log temporaryPassword, solo audit "USER_INVITED" + email |

---
