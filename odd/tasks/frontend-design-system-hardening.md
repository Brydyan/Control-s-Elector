# Frontend: endurecer el sistema de diseño y la lógica de campo

## Objetivo

Dejar el frontend migrado a PrimeNG 21 + Sakai en condiciones deservir un conteo electoral: que el modo oscuro exista de verdad, que la app tenga tipografía, que la cola offline no pueda perder ni duplicar un acta, que los roles funcionen, y que las 11 rutas que se anuncian en el menú entreguen lo que prometen.

**Rama base de la cadena:** `fix/service-worker-signal-state` (PR #138, head del stack).
**Estrategia de entrega:** un PR por fase, apilados sobre el stack abierto, en este orden:

```
#138  fix/service-worker-signal-state
  └─ #139  fix/role-and-locale
       └─ #140  fix/offline-queue-safety
            └─ #141  fix/observer-form
                 └─ #142  feat/dark-scheme
                      └─ #143  feat/typography
                           └─ #144  refactor/design-system-cleanup
                                └─ #145  feat/responsive-shell
                                     └─ #146  feat/a11y-hardening
                                          └─ #147  feat/clarify-copy
                                               └─ #148  perf/offline-and-bundle
                                                    └─ #149  docs/design-system
```

**Test runner:** `pnpm test:ci` (vitest) · `pnpm lint` · `pnpm build` — los tres deben quedar verdes en cada fase.

## Specs

### S1 — El rol MASTER debe detectarse desde la sesión, no desde una clave de storage que nadie escribe

Hoy `detectarRolMaster()` lee `sessionStorage/localStorage.getItem('app.user.roles')` y **nada en `src/` escribe esa clave** (el grep de `setItem` solo devuelve theme, drafts, PIN y sidebar). Por eso `esMaster` es permanentemente `false` y estos controles nunca renderizan, aunque la topbar diga MASTER:

- "Anular" acta — `features/coordinator/pages/actas-lista/actas-lista.page.html:215-225`
- "Crear usuario" — `features/admin/usuarios/admin-usuarios.page.html:7`
- habilitar / deshabilitar usuario — `features/admin/usuarios/admin-usuarios.page.html:110`
- anular usuario — `features/admin/usuarios/admin-usuarios.page.html:101,143`
- "Revocar" sesión — `features/admin/sesiones/admin-sesiones.page.html:72-74,93-95`

El JWT ya trae el rol y `roleGuard` lo usa. La detección debe salir de la misma fuente. Cuando el usuario es MASTER, los controles de MASTER deben existir y funcionar; cuando no lo es, deben seguir ocultos.

**Criterio de aceptación:** con una sesión MASTER, "Crear usuario", "Anular", habilitar/deshabilitar y "Revocar" aparecen y ejecutan su acción. Con una sesión ADMIN, siguen ocultos.

### S2 — La app debe speaks español en las fechas y los números

No hay `LOCALE_ID` ni `registerLocaleData('es')` en ningún lado, así que Angular renderiza en en-US. El mismo acta aparece con dos fechas distintas:

- `features/coordinator/pages/actas-lista/actas-lista.page.html:266` con `date:'short'` → "10/21/26, 12:45 AM"
- `features/coordinator/pages/actas-lista/actas-lista.page.html:205` con `date:'dd/MM/yyyy HH:mm'` → "21/10/2026 00:45"

También `features/admin/sesiones/admin-sesiones.page.html:88,131,133` y `features/admin/usuarios/admin-usuarios.page.html:234` con `'medium'` → "Oct 21, 2026, 12:45 AM", y los separadores de número saldrían como `1,234.56` en vez de `1.234,56`.

Todas las fechas de la app deben usar el mismo formato `dd/MM/yyyy HH:mm` (y `dd/MM/yyyy` cuando solo hay día), en los cinco archivos afectados. Ningún `date:'short'` ni `date:'medium'` debe quedar en los templates.

### S3 — La cola offline no puede borrar un acta ni duplicarla

`features/core/offline/sync.service.ts` tiene dos defectos que tocan un acta:

**Borrado (`:141-148`):** el `catch` incrementa `retries` ante **cualquier** error (red, 500, 4xx) y al llegar a `retries+1 >= 5` llama `eliminarPendiente(item.id)`, que borra el acta **y su blob de foto** de IndexedDB sin avisar al operador. El docstring de `:30-31` afirma que solo se abandona ante 4xx: el código no distingue. Cinco caídas de 30 s de conectividad destruyen el registro.

**Duplicado (`:110-140`):** el paso 1 (POST del acta) puede tener éxito mientras el paso 2 (POST de la imagen) falla; el item queda encolado y el siguiente reintento vuelve a hacer POST del acta. `enviarActaDirecta(item.payload)` no manda ninguna clave de idempotencia (`PendingActa.id` nunca se transmite) → **un acta duplicada en el servidor por una sola observación**.

Reglas:

1. Un error transitorio (red, 5xx, timeout) **nunca** borra el acta. Se reintenta con backoff y el blob se conserva.
2. Solo un rechazo permanente (4xx) mueve el item a un estado `failed` visible para el operador, y la eliminación exige una confirmación explícita.
3. El POST del acta debe llevar una clave de idempotencia derivada de `PendingActa.id`, para que un reintento no cree un segundo acta.
4. `pendientes` y `sincronizando` deben ser **visibles** en la interfaz: hoy ningún template los consume, así que el veedor que encoló un acta no ve nada.
5. El listener `online` de `:55` y su `removeEventListener` de `:76` usan identidades de función distintas → el listener nunca se quita y `stop()` es un no-op.

### S4 — Un mensaje de éxito no se renderiza como error

`features/observer/pages/registrar-acta/registrar-acta.page.ts:415-416` escribe el mensaje "Acta guardada en este dispositivo…" dentro de `this.error()`, y `app-error-state` lo pinta con `role="alert"` y color de peligro. Un acta que quedó encolada con seguridad **parece un fallo** e invita a duplicar el envío.

El estado de éxito tiene que ser un estado propio, con `role="status"`, y el error sigue siendo `role="alert"`.

### S5 — El número que el veedor ve tiene que ser el número que se registra

`features/observer/pages/registrar-acta/registrar-acta.page.ts:207-212` enlaza `[value]="getVoto(c.id)"` pero **nunca escribe de vuelta al DOM**: el valor enlazado no cambia, así que Angular no escribe. Escribir `-5` registra `0` mientras el campo sigue mostrando `-5` → el checksum discrepa de lo que el veedor ve.

El valor registrado y el valor mostrado tienen que ser siempre el mismo. Además el input no admite decimales ni valores fuera de rango, y el `max` no está definido.

### S6 — El veedor no puede quedar encerrado sin explicación

Con cero votos, `isChecksumValid` es falso (`registrar-acta.page.ts:238-243` exige `totalSufragantes>0` **y** igualdad) **y** `totalVotosComputados === 0` hace que el `*ngIf` del botón de escape "Continuar con Inconsistencia (Foto Requerida)" (`registrar-acta.page.html:171-190`) sea falso, mientras "Continuar a Evidencia Fotográfica" está `[disabled]`. Los dos caminos cerrados, sin explicación.

El botón de escape tiene que estar disponible siempre que el checksum no cuadre, y la diferencia concreta ("suma 12 ≠ padrón 15 — faltan 3") tiene que mostrarse, no solo un estado inválido.

Además, "Cancelar y volver" (`registrar-acta.page.html:5-11`) descarta los votos escritos sin avisar, y el paso 1 no tiene guardado de borrador (solo el paso 3 tiene "Guardar Borrador").

### S7 — El esquema oscuro tiene que existir

`src/theme/tokens.scss` declara **un solo `:root` light**; `grep -r app-dark` devuelve **0** en los SCSS. El `.app-dark` que PrimeNG aplica mueve solo su mitad de la superficie, así que el texto de la app sigue en `#0f172a` sobre una card `#0f172a`:

| Selector | Ratio actual |
|---|---|
| `h3.app-card__title` | **1.00** (los glifos no se pintan) |
| `label.form-label`, `span.candidate-name`, `span.info-value` | **1.00** |
| `p-tag.p-tag-info` | 1.46 |
| `p.historial-empty`, `div.canton-name` | 1.72 |
| `.layout-status-pill.status-online` | 1.90 |
| `.stat-card-value.v-corregida` | 2.05 |
| `.stat-card-label`, `span.info-label` | 2.36 |
| `.banner-head p` (en **claro**) | 2.12 |
| `.banner-head h2` (en **claro**) | 2.93 |

Son 31 pares que fallan en total (25 en oscuro, 6 en claro).

**La paleta de estados en claro ya es correcta** (7.29:1 a 8.01:1) y no hay que tocarla. La de oscuro hay que autorarla con la misma disciplina: *el tono es invariante, la claridad y la opacidad de la superficie no*.

- Superficies: las del ramp de PrimeNG en oscuro, para que no se vea la costura (`--p-content-background` contra `--color-surface-card`).
- Texto: fuera de blanco, nunca `#fff` (vibra sobre oscuro). Secundario y apagado con dos pasos más claros.
- Estados: foreground aclarado (`green-300`, `amber-300`, `red-300`, `blue-300`) sobre superficie translucida y borde con alfa.
- Bordes: mucho más sutiles que en claro.

Con esa regla, los pares hoy invisibles miden entre **6.96:1 y 16.30:1**.

Además, `src/theme/typography.scss:23-48` fuerza `h1..h6` y `p` a un color propio, lo que anula el color de los contenedores de color y rompe el banner azul **también en claro**. El color del texto tiene que heredarse, y cada contenedor con color propio declara el suyo.

### S8 — Trece custom properties están referenciadas y nunca definidas, once de ellas vivas

`--font-size-2xl` (hace que el `<h1>` de página se.renderice **más chico** que los `<h3>` de las cards), `--radius-full`, `--surface-subtle`, `--transition-fast`, `--color-success-subtle`, `--color-success-emphasis`, `--color-danger-subtle`, `--color-danger-emphasis`, `--border-success`, `--border-danger`, `--primitive-gray-500`, `--primitive-slate-500` (hace que el borde de estado se renderice `0px solid`) y `--color-status-info-bg`.

Dos consecuencias gravity: el **banner de checksum** (`registrar-acta.page.scss:208-217`) queda sin fondo ni borde, o sea que **la alerta matemática sobre la que está construido el producto no tiene contenedor visual**; y `--primitive-slate-500` anula la declaración completa del `border-left`, no solo el color.

Todas tienen que quedar definidas sobre la capa semántica, y ninguna puede quedar referenciada sin definición.

### S9 — La app tiene que declarar una tipografía

Nada declara `font-family`, así que **el contenido propio renderiza en la serif del UA** mientras el chrome de PrimeNG renderiza en Inter: dos familias tipográficas en las 14 rutas, visible en todas las capturas (por ejemplo "Control Electoral" junto a "Detalle de Acta").

Hace falta:

1. Una grotesk neutra y técnica, auto-alojada, con juego de cifras tabulares — la escala de `DESIGN.md` es neutra y técnica, y `PRODUCT.md:51` llama a las cifras tabulares *sagradas*.
2. `font-display: swap` y una métrica de reserva compatible.
3. El override de la tipografía de PrimeNG, para que Aura no pelee con la cara de la app: una sola familia, o un emparejamiento de dos documentado en `DESIGN.md`.
4. `tabular-nums` **en los números**: hoy `typography.scss:14-21` lo aplica a cinco selectores decorativos y a ninguno de las cifras que importan — las celdas `SUFRAGANTES` y `BLANCOS-NULOS` de la bandeja (`actas-lista.page.html:182-186`), la columna de votos del acta (`acta-detalle.page.html:240`), los inputs de voto (`registrar-acta.page.html:96-106`) y las cifras de los JRVs (`mis-jrvs.page.html:78-87`).
5. Una sola voz numérica: hoy las celdas de checksum usan `font-family: monospace` mientras las de votos al lado usan serif proporcional.

### S10 — La escala tipográfica tiene que tener un escalón que falta

`--font-size-2xl` no existe y tres páginas lo usan para su título (`registrar-acta.page.scss:19`, `mis-jrvs.page.scss:15`, `jrv-cargos.page.scss:15`): el `<h1>` de esas páginas se renderiza más chico que los `<h3>` de las cards de abajo. Y `h2` y `h3` comparten tamaño y peso, así que el nivel de card title es el mismo en todas las screens.

### S11 — Tres vocabularios de token no pueden convivir

Hoy conviven tres: `--p-*` de PrimeNG (80 referencias, 9 archivos), `--color-*` de la app (157 referencias, 15 archivos) y un vocabulario heredado sin definir (19 referencias, 0 definiciones). Solo 3 archivos mezclan las dos familias de verdad: `actas-lista.page.scss`, `admin-usuarios.page.scss` y `error-state.component.scss`.

El resultado es que el contrato de tokens es unenforceable. Además hay 26 tokens definidos y nunca usados (incluida toda la familia `--sidebar-*` heredada) y 13 CSS rules muertos: el stepper de `registrar-acta.page.scss:38-77`, el `.filter-tab` de `mis-jrvs.page.scss:26-58`, el `.form-input` de `registrar-acta.page.scss:109-128`, el `.form-error` de `login.page.scss:238` y el componente `estado-badge`, que se exporta y no lo usa ningún template.

Hay dos tablas de iconos (`shared/ui/icon-map.ts` con 60 nombres Ionic y el switch de 11 claves del backend en `sidebar.component.ts:156-179`) y dos paletas de estado paralelas (la de `tokens.scss` y la de `acta-detalle.page.scss:42-79,92-108`: dos verdes para el mismo significado).

Tiene que quedar **un** vocabulario, los muertos borrados, y un control de build que falle ante una `var()` sin definir o ante un hex fuera de `src/theme/`.

### S12 — El shell tiene que aguantar 390 px

A 390 px el bloque de acciones de la topbar mide ~394 px dentro de una caja de 366 px con `flex-shrink:0` (`layout.scss:96-101`), y el wordmark no puede truncar (`layout.scss:56-65`, `white-space:nowrap` sin `overflow:hidden`): **"En línea · Conectado" se pinta encima de "Control Electoral"**, que se lee "ctrol", y el badge de notificaciones tapa la campana (`layout.scss:206-214`). Visible en `m-dashboard-light.png`, `m-coordinator-actas-light.png` y `m-observer-registrar-light.png`.

Además `registrar-acta.page.scss` **no tiene ni una media query**: `.form-grid--2col` nunca colapsa y desborda 74 px a 390 px, 58 px a 820 px y 74 px al 200 % de zoom. El input de voto además sale de su propia card **a 1440 px**, porque `registrar-acta.page.html:101` no lleva la clase `.form-input` que aplica el `width:100%` de `:110`.

El shell necesita: wordmark truncable, pill de estado reducido al punto en móvil, badge reubicado, un breakpoint de tablet real (hoy `DESKTOP_BREAKPOINT` es 768 y no hay stop de 820/1024), y la bandeja con sus cinco pestañas de estado con la etiqueta completa (hoy colapsan a "En" + chevron, y dos de los cinco estados del acta quedan sin nombrar en un teléfono).

### S13 — Todo lo interactivo tiene que alcanzar 44 x 44 px

85 elementos interactivos visibles por debajo de 44 px en cada esquema a 390 px. Los peores: `button.layout-topbar-action` 16 x 40, `button.p-button.p-button-sm` 87.7 x 35, `a.layout-topbar-brand` 166 x 27, `a.p-steps-item-link` 32 x 61, e inputs de 42 px de alto. `DESIGN.md:90` promete 40 px en desktop y 44 px en móvil; hoy hay botones de 35 px.

### S14 — Las superficies del shell tienen que ceder paso a la app

`features/coordinator/pages/actas-lista/actas-lista.page.html:236-240` y `features/coordinator/pages/acta-detalle/acta-detalle.page.html:154-158` son `div` con `(click)`: sin `role`, sin `tabindex` y sin teclado. La foto de la evidencia y toda la lista de la bandeja en móvil son inalcanzables con teclado.

El foco tiene que ser visible y del color de la app: hoy hay reglas `:focus-visible` pero solo apuntan a componentes de PrimeNG, y los controles propios llevan el anillo por defecto del navegador. `outline:none` en `login.page.scss:202` y `registrar-acta.page.scss:117` (este último con un reemplazo de 1 px de `border-color`, demasiado débil).

`src/app/shared/ui/error-state/error-state.component.ts:20` y `empty-state.component.ts:22` fijan `<h3>`, y `card.component.html:7` hace lo mismo: **8 de las 14 rutas no tienen `<h1>`** y el salto de nivel viene de ahí, no de las páginas. Los componentes necesitan un `headingLevel` con default `h2`.

`aria-live` no existe en reposo: los dos que hay solo aparecen dentro del `*ngIf="loading()"` de los skeletons (`actas-lista.page.html:98,114`). No hay skip link ni `<meta name="theme-color">`.

Y `prefers-reduced-motion` no se respeta en ningún elemento: 19 elementos animan igual, y dos animan propiedades de layout (`layout.scss:22` `margin-left`, `layout.scss:263` `width`), lo que reflowa en cada toggle del sidebar.

### S15 — La copia tiene que decir lo que significa

Quince interpolaciones literales quedaron rotas por la conversión a signals: "291 usuarios()", "402 sesiones()", "Error al cargar actas()" — en `actas-lista.page.html:131,143,144`, `admin-usuarios.page.html:5,17,58,67,68`, `admin-sesiones.page.html:5,14,34,43,44` y `jrv-cargos.page.html:17,22,31`.

Además:

- "1 pendientes" — `mis-jrvs.page.html:72` necesita singular/plural.
- "Mis JRVs Asignadas" — `mis-jrvs.page.html:4,53` y `app.routes.ts:38`: JRV es masculino, y la misma mesa se llama "Mesa JRV-SE-001", "JRV-SE-002" y "JRV-1" en tres pantallas.
- El estado **pendiente** se renderiza con el badge `BORRADOR` (variante `secondary`, el gris más apagado de la pantalla) mientras lo ya registrado recibe el azul saturado: el estado sobre el que el veedor debe actuar es lo menos visible de la card (`jrv-cargos.page.html:46-49`, `mis-jrvs.page.html:69`).
- "Conexión segura con validación de credenciales electorales" se muestra servida por HTTP plano en el puerto 80.
- "¡Acta procesada exitosamente!" anuncia una máquina y no el acta del operador: sin id, sin totales, sin sello de tiempo del servidor.

### S16 — Las rutas que no existen tienen que decirlo

Seis rutas apuntan a `features/placeholder/placeholder.page.ts` (`app.routes.ts:59-71,100-127,159-203`) y el sidebar anuncia cuatro como si fueran módulos funcionando. La descripción de la ruta **reemplaza** el texto honesto del stub ("Módulo en construcción"), así que el usuario cree que la funcionalidad existe y no tiene forma de pedirla.

Los cuatro enlaces del menú tienen que quedar deshabilitados de forma explícita (`aria-disabled` y no navegables), llevando a una página que diga que el módulo no está construido. Los estados vacíos que ya existen se conservan, pero hay que diferenciar *primer uso* de *sin resultados*.

## Tasks

| ID | PR | S# | Route | Superficie |
|---|---|---|---|---|
| T1 | #139 `fix/role-and-locale` | S1, S2, S15 (admin) | delegated | `core/auth/**`, `features/admin/**`, `features/coordinator/pages/actas-lista/**`, `main.ts` |
| T2 | #140 `fix/offline-queue-safety` | S3, S4 | delegated | `core/offline/**`, `app/layout/topbar/**` |
| T3 | #141 `fix/observer-form` | S5, S6 | delegated | `features/observer/pages/registrar-acta/**` |
| T4 | #142 `feat/dark-scheme` | S7, S8 | inline | `theme/**`, `features/coordinator/pages/acta-detalle/**`, `features/placeholder/**` |
| T5 | #143 `feat/typography` | S9, S10 | inline | `theme/**`, `features/**` (cifras) |
| T6 | #144 `refactor/design-system-cleanup` | S11 | inline | `theme/**`, `shared/ui/**`, `shared/components/**`, `features/**` (css muerto) |
| T7 | #145 `feat/responsive-shell` | S12, S13 | inline | `app/layout/**`, `features/observer/pages/registrar-acta/**`, `features/coordinator/**` |
| T8 | #146 `feat/a11y-hardening` | S14 | inline | `shared/ui/**`, `shared/components/**`, `app/layout/**`, `features/**` |
| T9 | #147 `feat/clarify-copy` | S15, S16 | inline | `features/**`, `app.routes.ts`, `core/menu/**` |
| T10 | #148 `perf/offline-and-bundle` | S3 (SW) | inline | `main.ts`, `ngsw-config.json`, `app.routes.ts` |
| T11 | #149 `docs/design-system` | S11 (guard) | inline | `DESIGN.md`, `theme/**`, script de build |

Las tareas T1-T3 tienen superficies disjuntas y se ejecutan en paralelo. T4-T11 son secuenciales: tocan `theme/**` y las features, y cada una depende de que la anterior haya aterrizado.

## Log

- **L1 (2026-10-21, verbatim):** "pregunta" / "me gustarias que arregles todo en pr por fase apilandolos a los que ya estan abiertos osea seguir el stack pr"
- **L2 (2026-10-21):** La skill `impeccable` se ejecutó completa sobre el frontend migrado: critique dual-agent (A: `gentle-ai-explore`, B: `gentle-ai-verify`), audit técnico (9/20) y batería de 16 flujos. Snapshot en `.impeccable/critique/2026-10-21T07-10-25Z__frontend-app.md`, commit `7f0942a`.
- **L3 (2026-10-21):** P0 corregido en el camino: el PR #138 había introducido un crash en `acta-detalle` (el getter `detalle()` leía el signal fuera del guard y tiraba `TypeError` en cada visita). Fix `7051d96` + spec de regresión que carga el detalle de forma asíncrona, como la app real. 277 tests verdes.
- **L4 (2026-10-21):** Decisiones tomadas por el usuario: un PR por fase apilado sobre el stack abierto; "arreglar todo". Dos decisiones de producto sin respuesta explícita, resueltas por defecto y reversibles en un PR cada una: (a) el sidebar se queda con la superficie del tema —lo que ya está implementado y verificado— y `DESIGN.md` se actualiza para documentarlo; (b) los seis módulos placeholder se deshabilitan de forma honesta en vez de construirse, porque construirlos es producto nuevo y no un defecto.
- **L5 (2026-10-21):** El mirror de Engram de este documento queda pendiente: el runtime de `execute` se degradó a mitad de sesión y rejects todo el código por un error de parseo. Los hallazgos de la auditoría sí quedaron persistidos (obs 1870 y 1907).
