# Design — T-FE-18: Menús Dinámicos RBAC y Navegación Limpia en Sidebar

---

## D1 — Fuente de Verdad: Registro Declarativo en Frontend (`NAV_CONFIG`) vs Dependencia Ciega del Backend

**Decisión**:
Establecer un registro declarativo e inmutable en el frontend (`NAV_CONFIG: Record<UserRole, DynamicNavSection[]>`) ubicado en `features/layout/nav.config.ts` (o `core/menu/nav.config.ts`).
- La estructura de secciones, rutas canónicas, etiquetas e iconos para cada uno de los 4 roles (`OBSERVER`, `COORDINATOR`, `ADMIN`, `MASTER`) queda tipada y validada en tiempo de compilación.
- Cuando la sesión del usuario está activa en [`AuthStore`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/core/auth/auth.store.ts), `SidebarComponent` utiliza de forma reactiva e instantánea el conjunto de secciones correspondiente a su rol.
- Si el endpoint `GET /api/v1/menus/me` responde exitosamente, sus nodos se comparan, sanitizan y deduplican estrictamente contra la lista de rutas autorizadas canónicas de `NAV_CONFIG`, descartando cualquier ruta no contemplada en la matriz o asociada a componentes preliminares.

**Alternativa descartada — Dependencia ciega del árbol devuelto por `GET /api/v1/menus/me`**:
Descartada porque los seeds de base de datos en ambientes existentes contienen rutas legadas o desalineadas con la versión de la SPA. Si el cliente renderiza ciegamente lo que viene de la BD, se rompen la seguridad visual y la experiencia de usuario sin que el frontend tenga control.

---

## D2 — Erradicación Absoluta de Rutas a `PlaceholderPage` y Mapeo Canónico de Administración

**Decisión**:
Prohibir estrictamente en el menú lateral la inclusión de rutas que apunten a componentes huérfanos o páginas de relleno (`PlaceholderPage`), y establecer un mapeo canónico directo para los submenús observados en runtime:

1. **Mapeo Canónico para Submenús de Administración**:
   - `Gestión de usuarios` -> `/admin/usuarios` (Ruta operativa existente).
   - `Sesiones activas` -> `/admin/sesiones` (Ruta operativa existente).
   - `Cargos Electorales` -> `/master/configuracion` (Evita la ruta placeholder `/master/cargos` y navega a la vista consolidada de catálogos electorales per `T-FE-16`).
   - `Partidos Políticos` -> `/master/configuracion` (Evita la ruta placeholder `/master/partidos`).
   - `Candidatos` -> `/master/configuracion` (Evita la ruta placeholder `/master/candidatos`).

2. **Rutas Prohibidas (Blacklist Estricta en Sanitización)**:
   - `/actas` (ruta huérfana de placeholder; la ruta operativa real es `/coordinator/actas`).
   - `/evidencias` no cualificado y `/evidencias/subir` (flujo absorbido en `/coordinator/actas` y formulario de veedor).
   - `/admin` (redirección raíz no cualificada).
   - `/master/cargos`, `/master/partidos`, `/master/candidatos` (huérfanos desvinculados).

Cualquier nodo recibido desde la API que contenga estas rutas prohibidas es descartado o remateado canónicamente antes de emitirse al estado reactivo del sidebar.

**Alternativa descartada — Renderizar enlaces con estado deshabilitado (`disabled: true`)**:
Descartada porque mantener enlaces a páginas inexistentes o en construcción satura la interfaz con ruido innecesario, confunde a los usuarios en territorio y no aporta valor operativo en el día electoral.

---

## D3 — Eliminación de `mapRoute()` y Parches Procedimentales

**Decisión**:
Eliminar la función procedural `mapRoute(ruta: string)` y los bloques imperativos condicionales dispersos dentro de `SidebarComponent`.
- Las rutas configuradas en `NAV_CONFIG` son 100% canónicas y coinciden exactamente con las definiciones activas en [`app.routes.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/app.routes.ts).
- No se realiza ninguna traducción ni reescritura de URLs en tiempo de ejecución.
- Toda la construcción de navegación se vuelve puramente funcional, determinista y fácil de auditar mediante tests unitarios.

**Alternativa descartada — Mantener un diccionario de alias y normalización runtime**:
Descartada porque los alias ocultan inconsistencias arquitectónicas entre el backend y el frontend, añaden complejidad ciclomática innecesaria y dificultan la detección de rutas obsoletas.

---

## D4 — Resiliencia Offline y Cero Parpadeo (Zero-Flicker Offline UX)

**Decisión**:
El estado de las secciones del menú lateral se inicializa reactivamente a través de una señal computada o efecto basado en `store.user()?.role`:
- Al cargar la vista, el menú lateral presenta de inmediato las opciones de navegación correspondientes al rol autenticado sin esperar la respuesta HTTP de `GET /api/v1/menus/me`.
- Si el dispositivo se encuentra sin conexión a internet (modo offline en PWA) o el endpoint falla con error HTTP 500 / 504 / timeout, el menú permanece 100% interactivo y funcional utilizando `NAV_CONFIG`.
- Se evita cualquier parpadeo de pantalla (flickering), estados de carga bloqueantes en la barra lateral o pantallas vacías.

**Alternativa descartada — Bloquear el sidebar con spinner hasta completar `getMyMenus()`**:
Descartada porque en recintos electorales rurales de la península de Santa Elena con baja cobertura 3G/4G, un retraso o fallo de red dejaría al veedor o coordinador sin acceso a la navegación de la aplicación durante decenas de segundos.

---

## D5 — Desduplicación Estricta de Secciones e Ítems en el Pipeline de Renderizado

**Decisión**:
Incorporar una etapa de deduplicación determinista en el pipeline de transformación y ensamblado de secciones (`mapNodesToSections` / `sanitizeNavSections`), apoyada en estructuras indexadas con claves unívocas (`Map` y `Set`):

1. **Unicidad de Secciones por Clave Normalizada**:
   Las secciones se consolidan en un `Map<string, DynamicNavSection>` indexado por `sectionKey = section.label.trim().toLowerCase()`. Si la respuesta del backend y la configuración declarativa coinciden en una sección (por ejemplo, 'Operación Electoral' o 'Actas'), sus ítems se fusionan de manera ordenada y determinista, impidiendo la creación de secciones repetidas.

2. **Unicidad de Ítems por Ruta Canónica**:
   Dentro de cada sección, los ítems de navegación se registran contra un `Set<string>` de rutas ya procesadas (`item.route.trim()`). Si un ítem con la ruta `/observer/mis-jrvs` ya fue asignado a una sección o ya fue procesado, cualquier intento subsiguiente de re-inyección es descartado silenciosamente.

3. **Supresión Definitiva de la Doble Inyección Manual (L101-113)**:
   Se erradica por completo el bloque procedural en [`SidebarComponent`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/layout/sidebar/sidebar.component.ts):
   ```typescript
   // ELIMINADO DEFINITIVAMENTE:
   const role = this.user()?.role;
   if (role === 'OBSERVER' || role === 'MASTER') {
     sections.push({
       label: 'Operación Electoral',
       items: [
         {
           label: 'Mis JRVs',
           icon: 'grid-outline',
           route: '/observer/mis-jrvs',
         },
       ],
     });
   }
   ```
   Al remover esta inyección manual y aplicar el pipeline de desduplicación estricta, se garantiza que tanto `OBSERVER` como `MASTER` visualicen **exactamente una única vez** la sección "Operación Electoral" y el ítem "Mis JRVs", logrando **CERO duplicados** en el DOM.

**Alternativa descartada — Deduplicación condicional en la plantilla HTML con directivas visuales**:
Descartada porque ocultar duplicados en la capa de vista (`@if`, `*ngIf` por índice) mantiene la corrupción de datos en la señal del componente (`_sections`), penaliza el rendimiento del ciclo de detección de cambios de Angular y falsea las pruebas unitarias automatizadas.
