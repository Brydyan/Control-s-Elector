# Tasks — T-FE-18: Menús Dinámicos RBAC y Navegación Limpia en Sidebar

## Fase A: Definición de `NAV_CONFIG` y Modelos Tipados

- [x] **A.1** — Crear `features/layout/nav.config.ts` (o `core/menu/nav.config.ts`):
  - Definir interfaces tipadas: `NavSection`, `NavItem`, y tipo de unión de roles `UserRole` ('OBSERVER' | 'COORDINATOR' | 'ADMIN' | 'MASTER').
  - Definir el diccionario inmutable `NAV_CONFIG: Record<UserRole, NavSection[]>` con la matriz canónica exacta del `SRS.md`:
    - `OBSERVER`: Operación Electoral -> Mis JRVs (`/observer/mis-jrvs`), Mis Actas (`/observer/mis-actas`).
    - `COORDINATOR`: Principal -> Dashboard (`/dashboard`); Actas -> Aprobar / Rechazar (`/coordinator/actas`), Reportes CNE (`/coordinator/reportes`); Evidencias -> Subir evidencia (`/coordinator/actas`).
    - `ADMIN`: Principal -> Dashboard (`/dashboard`), Resultados (`/dashboard/resultados`), Progreso Cantonal (`/dashboard/progreso`).
    - `MASTER`: Principal (Dashboard, Resultados, Progreso Cantonal); Actas (Aprobar / Rechazar, Reportes CNE); Evidencias (Subir evidencia); Operación Electoral (Mis JRVs, Mis Actas); Configuración (Catálogos Electorales `/master/configuracion`); Administración (Gestión de usuarios `/admin/usuarios`, Sesiones activas `/admin/sesiones`).
  - Mapear canónicamente los submenús de Cargos, Partidos y Candidatos a `/master/configuracion`.
  - Definir lista de rutas prohibidas / placeholders (`PLACEHOLDER_ROUTES: string[] = ['/actas', '/evidencias', '/evidencias/subir', '/admin', '/master/cargos', '/master/partidos', '/master/candidatos']`).
  - Implementar función utilitaria pura de sanitización y deduplicación `sanitizeNavSections(role: UserRole, backendNodes?: MenuNode[]): NavSection[]`.
- [x] **A.2** — Tests unitarios de `nav.config.spec.ts`:
  - Validar que ningún rol contenga rutas presentes en `PLACEHOLDER_ROUTES`.
  - Validar que cada rol reciba exactamente las rutas operativas asignadas en el SRS sin duplicación interna.

---

## Fase B: Refactorización y Deduplicación en `SidebarComponent`

- [x] **B.1** — Eliminar parches procedimentales y doble inyección manual:
  - Eliminar definitivamente la función `mapRoute()`.
  - Eliminar el bloque procedural incondicional en L101-113:
    ```typescript
    if (role === 'OBSERVER' || role === 'MASTER') {
      sections.push({ label: 'Operación Electoral', items: [...] });
    }
    ```
- [x] **B.2** — Implementar pipeline de desduplicación estricta de secciones e ítems:
  - Emplear un `Map<string, DynamicNavSection>` indexado por `label.trim().toLowerCase()` para evitar secciones duplicadas.
  - Emplear un `Set<string>` por cada sección con las rutas canónicas ya registradas (`route.trim()`) para descartar cualquier ítem o ruta repetida (ej. `/observer/mis-jrvs`).
  - Garantizar que la consolidación de secciones preserve el orden canónico y descarte colisiones de forma determinista.
- [x] **B.3** — Conectar la inicialización del sidebar a la fuente de verdad declarativa:
  - Inicializar la señal `_sections` utilizando `NAV_CONFIG[role]` de forma reactiva ante cambios en `store.user()`.
  - En `cargarMenus()`, sanitizar y deduplicar la respuesta de `menuService.getMyMenus()` contra `NAV_CONFIG` del rol activo, asegurando que ningún nodo backend introduzca rutas placeholder ni duplicados en runtime.
- [x] **B.4** — Blindar resiliencia offline y manejo de fallos HTTP:
  - Si `getMyMenus()` falla con error HTTP 500, timeout o falta de red, capturar el error y garantizar que `_sections` mantenga o establezca de inmediato la configuración completa de `NAV_CONFIG` sin dejar la barra en blanco ni parpadear.
- [x] **B.5** — Ajustes en el template y accesibilidad:
  - Asegurar que `sidebar.component.html` renderice limpiamente las secciones y enlaces con sus atributos de accesibilidad (`aria-label`, `title`, routerLinkActive exacto para `/dashboard`).

---

## Fase C: Suite de Pruebas Unitarias de `SidebarComponent`

- [x] **C.1** — Cobertura completa de los 4 roles en `sidebar.component.spec.ts`:
  - Caso `OBSERVER`: verificar renderizado exclusivo de la sección 'Operación Electoral' con `/observer/mis-jrvs` y `/observer/mis-actas` (cero Dashboard, Actas o Administración).
  - Caso `COORDINATOR`: verificar renderizado de Principal (`/dashboard`), Actas (`/coordinator/actas`, `/coordinator/reportes`) y Evidencias (`/coordinator/actas`) (cero Registrar Acta ni Administración).
  - Caso `ADMIN`: verificar renderizado de Principal (`/dashboard`, `/dashboard/resultados`, `/dashboard/progreso`) (cero acciones de escritura).
  - Caso `MASTER`: verificar renderizado consolidado de todas las áreas operativas con CERO duplicados.
- [x] **C.2** — Test de Cero Placeholders en el DOM:
  - Iterar sobre todos los elementos `<a class="nav-item">` y verificar que ninguno tenga `routerLink` o `href` que coincida con `/actas`, `/evidencias` no cualificado, `/admin` crudo, `/master/cargos`, `/master/partidos` ni `/master/candidatos`.
- [x] **C.3** — Test de Resiliencia ante error HTTP 500:
  - Simular que `menuService.getMyMenus()` retorna `throwError(() => new HttpErrorResponse({ status: 500 }))` y verificar que el sidebar renderiza inmediatamente las rutas de `NAV_CONFIG` para el rol sin errores ni pantalla vacía.
- [x] **C.4** — Test de No-Duplicación en el Árbol DOM:
  - Validar que ninguna etiqueta de sección (`.nav-section-title`) se repita en el DOM.
  - Validar que ningún enlace `<a class="nav-item">` tenga un `routerLink` o `href` repetido en el sidebar.
  - Verificar explícitamente para `MASTER` y `OBSERVER` que la sección 'Operación Electoral' y la ruta `/observer/mis-jrvs` existan exactamente UNA vez (`count === 1`).

---

## Verificación por Mutación (Gate 3)

- [ ] **V.1** — Inyectar deliberadamente una ruta de placeholder (`/actas`) en la configuración del rol `OBSERVER` en `nav.config.ts`:
  - Comando: `npm test -- --run`
  - Resultado esperado: El test de "Cero Placeholders" en `sidebar.component.spec.ts` debe fallar.
  - Restaurar el cambio tras validar la falla.
- [ ] **V.2** — Modificar el manejador de error en `SidebarComponent` para establecer `_sections.set([])` ante fallo HTTP:
  - Comando: `npm test -- --run`
  - Resultado esperado: El test de "Resiliencia ante error HTTP 500" en `sidebar.component.spec.ts` debe fallar.
  - Restaurar el cambio tras validar la falla.
- [ ] **V.3** — Inyectar deliberadamente una sección o ítem duplicado en el pipeline del sidebar (reintroduciendo la inyección de `/observer/mis-jrvs`):
  - Comando: `npm test -- --run`
  - Resultado esperado: El test de "No-Duplicación en el Árbol DOM" (C.4) en `sidebar.component.spec.ts` debe fallar.
  - Restaurar el cambio tras validar la falla.

---

## Compuertas de QA (Gates)

- **Gate 0 (Compilación)**: `npx tsc -p tsconfig.app.json --noEmit` ejecuta sin ningún error de tipado.
- **Gate 1 (Pruebas Unitarias)**: `npm test -- --run` finaliza con el 100% de tests pasando en `frontend/app`.
- **Gate 2 (Mutaciones)**: V.1, V.2 y V.3 son ejecutadas, capturadas por los tests y restauradas a verde.
- **Gate 3 (Limpieza de Navegación y No-Duplicación)**: Cero rutas hacia `PlaceholderPage` y cero secciones/rutas duplicadas en el menú lateral de la aplicación.

---

## Qué NO hacer en esta fase

1. **NO modificar la base de datos ni migraciones Liquibase en backend**: El backend mantendrá su esquema actual; el blindaje, sanitización y deduplicación se resuelven en la capa frontend.
2. **NO eliminar `PlaceholderPage` ni borrar sus rutas en `app.routes.ts`**: Otras dependencias o pruebas podrían referenciarlas; este change se enfoca exclusivamente en erradicarlas de la navegación del `SidebarComponent`.
3. **NO reintroducir traducciones procedurales ni bloques `push` dispersos**: Toda la estructura de navegación debe residir en `NAV_CONFIG` con ensamblado determinista y desduplicado.
4. **NO bloquear la interfaz con spinners de carga en el sidebar**: La experiencia debe ser instantánea y offline-first.
