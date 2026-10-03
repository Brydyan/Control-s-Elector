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

Medidas el **2026-09-25**, rama `brydyan/sc-161/t-be-14-integracion-supabase-storage`:

```
backend build     ./mvnw -B clean package -DskipTests        exit 0
backend test      ./mvnw -B test                             63 suites / 281 tests
                                                             0 ERROR / 0 failure / 2 skip   ← VERDE
frontend lint     pnpm lint                                  0 errores
frontend format   pnpm format:check                          0 errores
frontend test     pnpm test:ci                               26 suites / 118 tests / 0 failures
```

Working directory obligatorio: backend en `backend/electoral-api/`, frontend en `frontend/app/`.

**Los números importan.** Si al añadir tests el total no sube, los tests nuevos no se están
ejecutando. Eso es un fallo de la fase, no un detalle.

**La suite de backend está 100% en VERDE**: el blocker inicial `78 ERROR` fue resuelto y archivado
en `openspec/changes/archive/2026-09-24-fix-liquibase-rbac-order/`. T-BE-13 (Dashboard) también
archivado. Los tests nuevos se distinguen y se miden con rigor contra esta línea base.

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
2. **`T-BE-16` (Expediente Legal & Estado `EN_OBSERVACION`)**: Archivado / Completado (Estado EN_OBSERVACION en DB y API).
3. **`T-BE-15` (Tests de integración E2E)**: Archivado / Completado (Tests de ciclo de vida completo).
4. **`T-BE-17` (Aislamiento RLS en MockMvc)**: Archivado / Completado.
5. **`T-BE-18` (Subida de Evidencia por Coordinador)**: `openspec/changes/back/2026-09-25-t-be-18-coordinator-evidencia-upload/`. Endpoint `POST /api/v1/coordinator/actas/{id}/imagen` para adjuntar foto física recibida por contingencia cuando el veedor queda sin batería o con teléfono dañado.
6. **`T-BE-19` (Incidencias Operativas de JRV sin Acta)**: `openspec/changes/back/2026-09-25-t-be-19-incidencias-jrv/`. Tabla `incidencia_jrv` y endpoints de reporte para alertar mesas no instaladas, impedimentos a veedores o disturbios sin necesidad de un acta de escrutinio.
7. **`T-BE-20` (Expediente de Impugnación CNE en PDF)**: `openspec/changes/back/2026-09-25-t-be-20-dossier-impugnacion-cne-pdf/`. Generador OpenPDF que compila en 1 página formal la foto del acta, el cuadro de votos, la inconsistencia detectada y sello forense para defensa legal en audiencia pública CNE.
8. **`T-BE-21` (Acta Detalle Completo DTO & GET Observer)**: `openspec/changes/back/2026-09-26-t-be-21-acta-detalle-dto-observer-get/`. DTO enriquecido `ActaDetalleCompletoDto` con votos individuales de candidatos, URL de evidencia física prefirmada y motivo de disputa, más endpoint `GET /api/v1/observer/actas/{id}`.

#### Fase 2 · Frontend Estabilización y Operación de Campo (Spec 2027)
1. **`2026-09-25-fe-vitest-ionic-cleanup-race`**: Aislar teardown de Vitest en `app.component.spec.ts` y eliminar `continue-on-error: true` de `.github/workflows/ci.yml`.
2. **Conciliación de SDDs implementadas**: Auditar y archivar retrospectivamente `T-FE-01` a `T-FE-07` y `T-FE-12` (que ya cuentan con 118 tests pasando en `frontend/app`).
3. **`T-FE-13` (Modo "Foto Primero" y Cotas Electorales)**: `openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/`. Reordenar wizard: 1: Foto -> 2: Votos -> 3: Revisión (salvaguarda ante desalojo militar); warning > 350 sufragantes, tope duro 500; cuadre estricto de votos y regla CPCCS (hasta 3 marcas); persistencia de borrador local en IndexedDB (`acta_drafts`) previa a captura para resistir cierres del navegador/LMK.
4. **`T-FE-08` (Compresión WebP y Optimización de RAM)**: Decodificación directa C++/GPU vía `createImageBitmap(blob, { resizeWidth: 1920 })`, compresión WebP en Web Worker con `OffscreenCanvas`, cero Base64 (blobs binarios puros), limpieza inmediata con `URL.revokeObjectURL()` y captura PWA con `<input type="file" accept="image/*" capture="environment">`.
5. **`T-FE-09` (Offline-First PWA, IndexedDB Blindada & PIN de 4 dígitos)**: Cola en IndexedDB blindada con `navigator.storage.persist()`, patrón "Guardar antes de capturar" en almacén `acta_drafts` con auto-recuperación ante reinicio de pestaña por Android LMK, Service Worker para app shell 100% offline, reintento 30s y **desbloqueo rápido por PIN offline** cuando el JWT expira sin cobertura.
6. **`T-FE-10` (Coordinador Split-View)**: Vista 50/50 desktop (datos + visor WebP con zoom/rotación) + acción `Pasar a Observación Legal` + botón de contingencia WhatsApp `wa.me/` + descarga de expediente PDF CNE.
7. **`T-FE-11` (Dashboard Reactivo)**: Backend Authority en agregaciones de PostgreSQL con Supabase Realtime como gatillo debounced (500ms).
8. **`T-FE-14` (Observer Corrección de Acta Disputada)**: `openspec/changes/front/2026-09-26-t-fe-14-observer-correccion-acta/`. Pantalla móvil `/observer/acta/:id/corregir` con motivo de coordinador, visor de foto original, checksum dinámico y reenvío transaccional.
9. **`T-FE-15` (Dashboard Analítico: Resultados por Cargo y Progreso Cantonal)**: `openspec/changes/front/2026-09-26-t-fe-15-dashboard-resultados-progreso/`. Vistas `/dashboard/resultados` y `/dashboard/progreso` con tabs de dignidades, líder provisional, barras de porcentajes y avance cantonal de Santa Elena.
10. **`T-FE-16` (Módulo Configuración Preelectoral MASTER)**: `openspec/changes/front/2026-09-26-t-fe-16-master-configuracion-electoral/`. Vista unificada `/master/configuracion` con tabs `Cargos`, `Partidos Políticos` y `Candidatos`, modales de alta/edición y CRUD con la API backend.
11. **`T-FE-17` (Centro de Reportes y Expedientes CNE para Coordinador)**: `openspec/changes/front/2026-09-26-t-fe-17-coordinator-reportes-estadisticas/`. Vista `/coordinator/reportes` con panel de avance global, distribución de estados, métricas de resolución y descarga de expedientes de impugnación PDF.

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

Última actualización: **2026-09-25**, por el Arquitecto.
Changes activos / preparados:
- `openspec/changes/back/2026-09-25-t-be-18-coordinator-evidencia-upload/`
- `openspec/changes/back/2026-09-25-t-be-19-incidencias-jrv/`
- `openspec/changes/back/2026-09-25-t-be-20-dossier-impugnacion-cne-pdf/`
- `openspec/changes/front/2026-09-25-t-fe-13-capture-and-run-foto-primero/`
- `openspec/changes/front/2026-09-25-fe-vitest-ionic-cleanup-race/`
- `openspec/changes/front/2026-09-07-t-fe-{06..11}/` (alineados con backend 2027)
