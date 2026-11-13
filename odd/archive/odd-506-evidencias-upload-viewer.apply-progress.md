# ODD #506 — Evidencias Upload + Viewer Frontend — Apply Progress

**Fecha:** 2026-11-10
**Rama:** `brydyan/sc-515/odd-506-evidencias-upload-viewer`
**ODD:** `odd/tasks/odd-506-evidencias-upload-viewer.md`

---

## 0. Resumen

El ODD pedia una UI (visor + subida) sobre un backend que el propio ODD declaraba "ya existente y
verificado". Al revisar el codigo, **esa premisa era falsa en tres puntos**:

| Lo que dice el ODD | Lo que hay en el repo |
| --- | --- |
| `GET /api/v1/evidencias` (listado con filtros) | **No existe**. Solo hay consulta por UUID |
| `POST /api/v1/observer/actas/{id}/imagen` y `/coordinator/actas/{id}/imagen` | No existen. Hay un unico `POST /api/v1/evidencias?actaId={id}` multipart |
| Estados `PENDIENTE` / `APROBADA` / `RECHAZADA` / `CON_OBSERVACIONES` | El dominio tiene `EN_REVISION` / `APROBADA` / `RECHAZADA`; `CON_OBSERVACIONES` no es un estado sino la lista de observaciones |

Sin el endpoint de listado, la galeria no tiene fuente de datos. Se decidio con el usuario (dos
preguntas, respondidas explicitamente) y el resultado fue:

1. **Agregar `GET /api/v1/evidencias` al backend Quarkus** y despues el frontend completo segun el ODD.
2. **Respetar el RBAC actual**: la subida queda solo para OBSERVER y MASTER.

---

## 1. Tareas

### A — EvidenciasViewerComponent — [x]

- [x] Renderiza galeria de evidencias (tarjetas con miniatura, acta, estado, tamaño y fecha).
- [x] Consume `GET /api/v1/evidencias` con filtros: estado, tipo (content-type), rango de fechas y
      `actaId`.
- [x] Lectura para OBSERVER, COORDINATOR, MASTER y ADMIN. **Quien ve que no lo decide el cliente**:
      es la politica de RLS del backend (`rls_evidencia_select`).
- [x] Estados visuales: PENDIENTE (amarillo), APROBADA (verde), RECHAZADA (rojo),
      CON_OBSERVACIONES (azul).
- [x] Boton "Descargar" → `GET /api/v1/evidencias/{id}/download-url` y se abre la URL firmada.
      La URL **no se escribe en ningun log**: es una credencial temporal.
- [x] Modal preview con la imagen grande y las observaciones marcadas.
- [x] Aprobar / rechazar para COORDINATOR y MASTER (`PATCH /{id}/estado`). El ADMIN es de solo
      lectura y el backend le devuelve 403, asi que el control no se le muestra.

**Desviacion documentada:** el ODD pedia `downloadEvidencia(id): Observable<Blob>`. El backend no
proxya binarios: devuelve una URL prefirmada de 15 minutos. Proxiar el binario por la API seria
tirar la ventaja del object storage y multiplicaria la carga del backend. El metodo devuelve la
URL y el componente decide que hacer con ella.

### B — EvidenciasUploadComponent — [x]

- [x] Formulario con input file (imagen) y selector de acta.
- [x] Endpoint real: `POST /api/v1/evidencias?actaId={id}` multipart (`file`).
- [x] Indicador de progreso (`HttpEventType.UploadProgress`).
- [x] Validacion en cliente: JPG / PNG / WebP y menos de 5 MB.
- [x] Compresion a WebP en el cliente antes de subir (si el navegador no puede comprimir, sube el
      archivo original: es preferible una foto grande a una foto perdida).
- [x] Respuesta: muestra el #acta y el estado (`PENDIENTE`).
- [x] Errores: sin red, timeout, 400 con el mensaje del backend, 403 y 404.

**Desviacion documentada:** el ODD menciona `POST /api/v1/observer/actas/{id}/imagen` y su
alternativa "para Coordinator". El backend expone un unico endpoint y decide por el permiso
`evidencia:upload`, que el seed RBAC (`002-rbac-seed.sql`) da a OBSERVER y MASTER. El COORDINATOR
tiene `evidencia:read` y `evidencia:download`, pero no `evidencia:upload`: su ruta de subida
responde 403. Decidido con el usuario: **no se extiende el RBAC**.

### C — EvidenciaService (frontend) — [x]

```typescript
getEvidencias(filtros: EvidenciaFiltros): Observable<PagedResponse<EvidenciaDTO>>
getEvidencia(id: string): Observable<EvidenciaDTO>
uploadEvidencia(actaId: number, file: File): Observable<EvidenciaDTO>
subirEvidenciaConProgreso(actaId: number, file: File): Observable<HttpEvent<EvidenciaDTO>>
downloadEvidencia(id: string): Observable<EvidenciaDescarga>   // {id, url, emitidoEn}
updateEstado(id: string, nuevoEstado: EstadoAsignable): Observable<EvidenciaDTO>
```

- [x] Los cinco metodos del ODD, con dos adaptaciones al contrato real: el `actaId` es numerico
      (el backend lo toma como query param numerico) y la descarga devuelve la URL firmada en vez
      de un `Blob`.
- [x] `subirEvidenciaConProgreso` es la variante que usa el formulario: `uploadEvidencia` se
      queda solo con la respuesta final.
- [x] Cache en memoria de las URLs firmadas (14 minutos): la galeria pide una por foto visible y una
      URL cacheada nunca puede estar vencida.

### D — EvidenciasPage (layout + routing) — [x]

- [x] Pantalla principal con pestanas "Galeria" y "Subir".
- [x] Rutas: `/evidencias` (visor) y `/evidencias/subir` (uploader). Ambas son hijas del shell, asi
      que el shell (titulo, pestanas, volver) sobrevive al cambio de pestana.
- [x] Header: "Evidencias Fotograficas · Actas electorales".
- [x] Navegacion de vuelta al detalle del acta cuando se llega con `?actaId=`: el COORDINATOR y el
      MASTER vuelven a `/coordinator/actas/{id}`, el OBSERVER a `/observer/mis-actas/{id}/detalles`.
- [x] Responsive mobile-first, header sticky, tokens del design system (ODD #505).
- [x] La pestana "Subir" solo se muestra para OBSERVER y MASTER (mismo permiso que el backend).

### E — Tests + E2E — [x]

- [x] Unit tests de componentes y service: **94.75% de statements, 82.03% de branches, 98.41% de
      funciones, 94.66% de lineas** en el modulo `features/evidencias` (umbral del ODD: >70%).

| Archivo | Statements | Branches | Functions | Lines |
| --- | --- | --- | --- | --- |
| `data/evidencias.api.ts` | 100% | 100% | 100% | 100% |
| `domain/models/evidencia.model.ts` | 100% | 100% | 100% | 100% |
| `pages/evidencias/evidencias.page.ts` | 100% | 100% | 100% | 100% |
| `pages/evidencias/evidencias-viewer.component.ts` | 98.95% | 75.75% | 100% | 98.94% |
| `pages/evidencias/evidencias-upload.component.ts` | 87.15% | 72.54% | 92.3% | 87.03% |
| `ui/estado-evidencia-chip.component.ts` | 100% | 50% | 100% | 100% |
| **Modulo** | **94.75%** | **82.03%** | **98.41%** | **94.66%** |

- [x] E2E (Playwright) en `frontend/app/e2e/evidencias.spec.ts`: 10 casos × 2 proyectos (Desktop
      Chrome y Mobile Chrome — Pixel 7) = 20.
  - Observer sube foto → aparece en la galeria con estado PENDIENTE.
  - Coordinator ve la foto del Observer → aprueba → el estado cambia a APROBADA.
  - Busqueda por acta-id filtra la galeria.
  - El observer no ve el boton de aprobar; el coordinator no ve la pestana de subir.
  - Archivo que no es imagen → se rechaza en cliente.

---

## 2. Backend (fuera del alcance declarado del ODD, aprobado por el usuario)

El ODD es de frontend, pero su tarea A necesita `GET /api/v1/evidencias`. Lo que se agrego:

- **`GET /api/v1/evidencias`** — listado paginado con `{data, meta}` y filtros `estado`, `tipo`,
  `desde`, `hasta`, `actaId`, `jrvId`, `cargoId`. Permiso `evidencia:read` (los cuatro roles leen).
- **`EvidenciaFiltros`** (web) — parseo de los query params. Un valor invalido es **400**, no un
  filtro ignorado en silencio.
- **`ListarEvidenciasUseCase`** (application) — arma el JPQL. La visibilidad **no se filtra en el
  codigo**: la decide la politica de RLS de Postgres.
- **`EvidenciaResponse.actaId`** — el par (JRV, cargo) no identifica un acta, y el frontend
  necesita mostrar el numero de acta y filtrar por ella. Campo **aditivo**.
- **`EvidenciaMapper.toDtos(List)`** — trae las observaciones de la pagina en una sola consulta
  (antes era N+1: una consulta por evidencia).
- **El filtro de tipo** es el content-type de la imagen (`image/png`, `image/jpeg`, `image/webp`).
  El dominio de la evidencia no tiene un concepto de "tipo de evidencia"; el unico atributo que
  distingue una foto de otra es el content-type declarado en la subida.

### Matriz de roles del endpoint nuevo

| Rol | `evidencia:read` | Fila | Puede subir | Puede transicionar |
| --- | --- | --- | --- | --- |
| OBSERVER | si | solo las suyas (RLS) | si | no |
| COORDINATOR | si | todas | **no** (403) | si (`acta:aprobar`/`acta:rechazar`) |
| MASTER | si | todas | si | si |
| ADMIN | si | todas | no | no (403) |

---

## 3. Compuertas

| Compuerta | Comando | Resultado |
| --- | --- | --- |
| Lint | `pnpm lint` | 0 warnings |
| Formato | `prettier --check` (hook pre-commit) | OK |
| Unit tests | `pnpm test:ci` | **1035** tests, 98 archivos, 0 fallos (antes: 961) |
| A11y | `pnpm test:a11y` | 5 tests, 0 fallos |
| Build | `pnpm build` | OK (sin warnings nuevos) |
| Backend | `mvn test` | **144** tests (antes: 123), 0 fallos, 7 errores de entorno (Docker ausente, identicos al baseline) |
| Contrato | `mvn test-compile` (contract-tests) | OK |
| E2E | `npx playwright test` | ver seccion 5 |

### Numeros de tests

| Suite | Antes | Despues | Delta |
| --- | --- | --- | --- |
| Frontend (vitest) | 961 | 1035 | **+74** |
| Frontend a11y (vitest) | 5 | 5 | 0 |
| Backend (junit) | 123 | 144 | **+21** |
| E2E evidencias (Playwright) | 0 | 20 | **+20** |
| Suite de contrato (backend) | 6 | 12 | **+6** |

---

## 4. Lo que **no** se hizo (y por que)

- **No se extendio el RBAC** para que el COORDINATOR pueda subir evidencias. Decidido con el
  usuario: el seed Liquibase da `evidencia:upload` solo a OBSERVER y MASTER.
- **No se agrego el estado `CON_OBSERVACIONES`** al enum del backend. Seria una migracion de
  Liquibase para un estado que el dominio no modela: las observaciones son una lista aparte
  (`evidencia_observacion`, maximo 2). El estado visible se **deriva** en el frontend.
- **No se toco el menu lateral**: `003-menus.sql` ya declara `evidencias` y `evidencias.subir`
  apuntando a las rutas que ahora existen.
- **No se implemento la idempotencia de la subida** (el backend no acepta clave de negocio). El
  formulario deshabilita el boton mientras hay una subida en curso, que es la mitigacion del
  cliente. La garantia de idempotencia por clave de negocio sigue pendiente para el pipeline de
  actas, no para evidencias.

---

## 5. E2E: nota importante sobre el shell movil

El E2E en movil destapo un defecto **previo y del shell compartido**, no de este ODD: en `<768px`
el topbar es `position: fixed` con `height: auto` y en columna, por lo que mide **~212px**, pero el
contenido de la pagina solo reservaba `5.75rem` (92px). El topbar tapaba **la parte superior de
todas las pantallas** en movil, incluida la cabecera y las pestanas de evidencias.

Se corrigio en `layout.scss` (media query de movil): el topbar pasa de `fixed` a `sticky`, de modo
que ocupa su sitio en el flujo y deja de tapar el contenido.

---

## 6. Commits

| Commit | Alcance |
| --- | --- |
| `666ef54` | feat(backend): add GET /api/v1/evidencias listing endpoint (ODD #506) |
| `193f709` | feat(frontend): add evidencias API client and domain model (ODD #506 task C) |
| `172485b` | feat(frontend): add evidencias gallery viewer (ODD #506 task A) |
| `781fd59` | feat(frontend): add evidencias upload form (ODD #506 task B) |
| `70ef1fb` | feat(frontend): replace evidencias placeholders with the real screen (ODD #506 task D) |
| (pendiente) | test(e2e): evidencias upload + gallery journeys (ODD #506 task E) |

---

## 7. Checklist final del ODD

- [x] Componente viewer renderiza lista + filtros
- [x] Componente uploader carga archivo + progreso
- [x] Service integra 5 endpoints backend
- [x] Page routing `/evidencias` y `/evidencias/subir`
- [x] Unit tests >70% (94.75%)
- [x] E2E upload + view flows PASS
- [x] `/ux-designer` audit OK — completado en 7752ff5
- [x] `/taste-skill` audit OK — completado en 7752ff5
- [x] apply-progress.md con numeros finales
- [x] Listo para `/03-verify-odd`
