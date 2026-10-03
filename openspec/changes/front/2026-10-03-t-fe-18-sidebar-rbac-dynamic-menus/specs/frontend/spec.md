# Spec — T-FE-18: Menús Dinámicos RBAC y Navegación Limpia en Sidebar

## Escenarios de Especificación

### S1 — Navegación Exclusiva para Rol OBSERVER

```gherkin
GIVEN    un usuario autenticado en AuthStore con rol 'OBSERVER'
WHEN     SidebarComponent se inicializa
THEN     la lista de secciones contiene ÚNICAMENTE la sección 'Operación Electoral'
AND      los ítems de navegación son exactamente:
         | Etiqueta    | Ruta               | Icono                |
         | Mis JRVs    | /observer/mis-jrvs | grid-outline         |
         | Mis Actas   | /observer/mis-actas| document-text-outline|
AND      no contiene la sección 'Principal' ni la ruta '/dashboard'
AND      no contiene la sección 'Actas' ni la acción 'Aprobar / Rechazar'
AND      no contiene la sección 'Evidencias'
AND      no contiene la sección 'Administración' ni rutas administrativas (/admin/*, /master/*)
```

### S2 — Navegación Operativa y Supervisión para Rol COORDINATOR

```gherkin
GIVEN    un usuario autenticado en AuthStore con rol 'COORDINATOR'
WHEN     SidebarComponent se inicializa
THEN     la lista de secciones contiene las opciones autorizadas:
         | Sección     | Etiqueta            | Ruta                  | Icono                    |
         | Principal   | Dashboard           | /dashboard            | home-outline             |
         | Actas       | Aprobar / Rechazar  | /coordinator/actas    | checkmark-circle-outline |
         | Actas       | Reportes CNE        | /coordinator/reportes | bar-chart-outline        |
         | Evidencias  | Subir evidencia     | /coordinator/actas    | camera-outline           |
AND      no contiene la acción 'Registrar Acta'
AND      no contiene la sección 'Operación Electoral' ni rutas de veedor (/observer/*)
AND      no contiene la sección 'Administración' ni módulos de gestión de usuarios o catálogos
```

### S3 — Navegación Analítica para Rol ADMIN

```gherkin
GIVEN    un usuario autenticado en AuthStore con rol 'ADMIN'
WHEN     SidebarComponent se inicializa
THEN     la lista de secciones contiene las opciones autorizadas de solo lectura/analítica:
         | Sección     | Etiqueta         | Ruta                  | Icono              |
         | Principal   | Dashboard        | /dashboard            | home-outline       |
         | Principal   | Resultados       | /dashboard/resultados | pie-chart-outline  |
         | Principal   | Progreso Cantonal| /dashboard/progreso   | trending-up-outline|
AND      no contiene ninguna acción operativa de escritura
AND      no contiene las secciones 'Actas', 'Evidencias' ni 'Operación Electoral'
```

### S4 — Navegación Integral Estructurada y Consolidada para Rol MASTER

```gherkin
GIVEN    un usuario autenticado en AuthStore con rol 'MASTER'
WHEN     SidebarComponent se inicializa
THEN     la lista de secciones contiene la totalidad de las áreas operativas estructuradas y consolidadas:
         | Sección             | Etiqueta              | Ruta                  | Icono                    |
         | Principal           | Dashboard             | /dashboard            | home-outline             |
         | Principal           | Resultados            | /dashboard/resultados | pie-chart-outline        |
         | Principal           | Progreso Cantonal     | /dashboard/progreso   | trending-up-outline      |
         | Actas               | Aprobar / Rechazar    | /coordinator/actas    | checkmark-circle-outline |
         | Actas               | Reportes CNE          | /coordinator/reportes | bar-chart-outline        |
         | Evidencias          | Subir evidencia       | /coordinator/actas    | camera-outline           |
         | Operación Electoral | Mis JRVs              | /observer/mis-jrvs    | grid-outline             |
         | Operación Electoral | Mis Actas             | /observer/mis-actas   | document-text-outline    |
         | Configuración       | Catálogos Electorales | /master/configuracion | settings-outline         |
         | Administración      | Gestión de usuarios   | /admin/usuarios       | people-outline           |
         | Administración      | Sesiones activas      | /admin/sesiones       | pulse-outline            |
AND      los submenús de Cargos, Partidos y Candidatos están mapeados canónicamente a '/master/configuracion'
AND      la sección 'Operación Electoral' y la ruta '/observer/mis-jrvs' se renderizan exactamente UNA vez con CERO duplicados
```

### S5 — Erradicación Absoluta de Rutas Placeholder en Sidebar

```gherkin
GIVEN    cualquier usuario autenticado con cualquier rol ('OBSERVER', 'COORDINATOR', 'ADMIN', 'MASTER')
AND      independientemente de los nodos recibidos desde GET /api/v1/menus/me
WHEN     el sidebar renderiza los elementos <a> y nav-items en el DOM
THEN     ningún atributo href o routerLink contiene:
         - /actas
         - /evidencias (ruta huérfana no calificada)
         - /evidencias/subir
         - /admin (ruta raíz no calificada)
         - /master/cargos
         - /master/partidos
         - /master/candidatos
AND      el 100% de los enlaces visibles corresponden a componentes funcionales registrados
```

### S6 — Resiliencia Inmediata ante Error HTTP 500 o Falla Offline en `GET /menus/me`

```gherkin
GIVEN    un usuario autenticado con rol 'OBSERVER' (o cualquier otro rol)
AND      el servicio MenuService.getMyMenus() falla con error HTTP 500 o error de conexión de red
WHEN     SidebarComponent ejecuta cargarMenus() o inicializa sus secciones
THEN     el componente no parpadea ni deja el menú en blanco
AND      las secciones de navegación se resuelven inmediatamente desde NAV_CONFIG
AND      el usuario puede navegar y hacer clic en sus opciones autorizadas sin impedimento
```

### S7 — Garantía de No-Duplicación en el Árbol de Navegación (Cero Duplicados en DOM)

```gherkin
GIVEN    cualquier usuario autenticado con rol 'MASTER', 'OBSERVER', 'COORDINATOR' o 'ADMIN'
WHEN     SidebarComponent renderiza las secciones e ítems en el DOM
THEN     ninguna etiqueta de sección (.nav-section-title) se renderiza más de una vez en el DOM
AND      ningún ítem de navegación (<a>) presenta un 'routerLink' o 'href' duplicado dentro del árbol del sidebar
AND      para los roles 'MASTER' y 'OBSERVER', la sección 'Operación Electoral' y el enlace '/observer/mis-jrvs' aparecen con conteo de ocurrencias exactamente igual a 1
AND      no existe inyección residual ni duplicación al final del menú lateral
```

---

## Garantías

- **G1 (RBAC Estricto)**: Ningún usuario puede visualizar enlaces a secciones que no correspondan a su rol activo según [`SRS.md`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/SRS.md).
- **G2 (Cero Placeholders)**: Se erradica por completo la presencia de rutas placeholder en el árbol de navegación del sidebar.
- **G3 (Resiliencia Offline Zero-Flicker)**: El sidebar se alimenta prioritariamente de la configuración declarativa `NAV_CONFIG`, garantizando operatividad instantánea en entornos de baja o nula conectividad.
- **G4 (No-Duplicación e Integridad Estructural)**: Cero duplicados en secciones y rutas en el DOM renderizado, asegurando un árbol de navegación unívoco y determinista.

---

## Verificación por Mutación Canónica

- **V.1 (Inyección de Ruta Placeholder)**:
  - *Mutación*: Insertar deliberadamente `{ label: 'Actas Legacy', route: '/actas', icon: 'document' }` en `NAV_CONFIG.OBSERVER`.
  - *Comportamiento esperado*: La prueba de "Cero Placeholders" en `sidebar.component.spec.ts` debe fallar inmediatamente al detectar una ruta prohibida en el DOM.
- **V.2 (Anulación de Fallback Declarativo ante Falla de Red)**:
  - *Mutación*: Modificar la captura de error en `SidebarComponent` para asignar `[]` (arreglo vacío) cuando `getMyMenus()` falla.
  - *Comportamiento esperado*: La prueba de "Resiliencia ante error HTTP 500" en `sidebar.component.spec.ts` debe fallar al esperar secciones y enlaces renderizados en el DOM.
- **V.3 (Inyección de Sección o Ítem Duplicado)**:
  - *Mutación*: Inyectar deliberadamente una sección duplicada (ej. reintroducir la inyección procedural de 'Operación Electoral' con `/observer/mis-jrvs`) en el pipeline de renderizado del sidebar.
  - *Comportamiento esperado*: La prueba unitaria de deduplicación (S7) en `sidebar.component.spec.ts` debe fallar inmediatamente al detectar secciones o rutas repetidas en el DOM.
