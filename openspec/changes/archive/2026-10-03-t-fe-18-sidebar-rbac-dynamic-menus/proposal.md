# Proposal — T-FE-18: Menús Dinámicos RBAC y Navegación Limpia en Sidebar

## Qué se construye

Refactorización profunda y estabilización de [`SidebarComponent`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/layout/sidebar/sidebar.component.ts) para garantizar que cada rol (`OBSERVER`, `COORDINATOR`, `ADMIN`, `MASTER`) visualice exclusivamente sus rutas operativas autorizadas en la interfaz web y PWA, asegurando:
1. **Navegación RBAC Estricta por Rol**: Renderizado determinista del menú de navegación acorde a la especificación de requerimientos de software ([`SRS.md`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/documents/SRS.md) §RF-MENU-001).
2. **Erradicación Total de Enlaces a `PlaceholderPage`**: Eliminación absoluta de hipervínculos o ítems en el menú lateral que apunten a rutas huérfanas o páginas preliminares en construcción (tales como `/actas`, `/evidencias` no cualificado, `/admin` no calificado, `/master/cargos`, `/master/partidos`, `/master/candidatos`).
3. **Eliminación de `mapRoute()` y Doble Inyección Procedural**: Descarte de funciones heurísticas de reescritura en cliente (`mapRoute()`) y remoción del bloque procedural de inyección manual en [`SidebarComponent`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/layout/sidebar/sidebar.component.ts) (L101-113) en favor de un registro declarativo canónico y pipeline desduplicado.
4. **Garantía Estricta de No-Duplicación (Cero Duplicados)**: Eliminación del defecto crítico en runtime donde "Operación Electoral - Mis JRVs" aparecía duplicado al final del menú para roles con acceso electoral.
5. **Desacoplamiento de Seeds de Base de Datos Desactualizados**: Blindaje del frontend frente a desalineaciones o datos legados provistos por el endpoint `GET /api/v1/menus/me`, estableciendo una fuente de verdad tipada e inmutable en el cliente con sanitización y deduplicación estricta.
6. **Resiliencia Offline Instantánea (Zero-Flicker UX)**: Carga inmediata del menú lateral utilizando la sesión activa de [`AuthStore`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/core/auth/auth.store.ts), garantizando operatividad total aún ante fallas de red, latencia o errores HTTP 500 del servidor.

---

### Taxonomía Observada en Runtime vs Matriz Canónica Consolidada

La estructura observada por el usuario en runtime consta de cinco secciones principales y sus respectivos sub-ítems:

```text
1. Principal
   - Dashboard (/dashboard)
2. Actas
   - Registrar Acta (/observer/mis-jrvs)
   - Aprobar / Rechazar (/coordinator/actas)
3. Evidencias
   - Subir evidencia (/coordinator/actas - contingencia coordinador / flujo de evidencia)
4. Operación Electoral
   - Mis JRVs (/observer/mis-jrvs)  --> ¡DUPLICADO detectado en runtime!
   - Mis Actas (/observer/mis-actas)
5. Administración
   - Gestión de usuarios (/admin/usuarios)
   - Sesiones activas (/admin/sesiones)
   - Cargos Electorales (/master/configuracion) [mapeo canónico anti-placeholder]
   - Partidos Políticos (/master/configuracion) [mapeo canónico anti-placeholder]
   - Candidatos (/master/configuracion) [mapeo canónico anti-placeholder]
```

---

### Reglas de Filtrado RBAC por Rol

| Rol | Secciones Visibles | Submenús y Rutas Autorizadas | Restricciones Estrictas de Seguridad |
|---|---|---|---|
| **`OBSERVER`** | **Operación Electoral** | • Mis JRVs (`/observer/mis-jrvs`)<br>• Mis Actas (`/observer/mis-actas`) | **Solo ve Operación Electoral**. Prohibido visualizar "Principal" (Dashboard), "Actas" ("Aprobar / Rechazar"), "Evidencias" o "Administración". |
| **`COORDINATOR`** | **Principal**<br>**Actas**<br>**Evidencias** | • Dashboard (`/dashboard`)<br>• Aprobar / Rechazar (`/coordinator/actas`)<br>• Subir evidencia (`/coordinator/actas`) | **Solo ve supervisión y contingencia**. Prohibido visualizar "Registrar Acta", "Mis JRVs", "Mis Actas" ni "Administración". |
| **`ADMIN`** | **Principal** | • Dashboard (`/dashboard`)<br>• Resultados (`/dashboard/resultados`)<br>• Progreso Cantonal (`/dashboard/progreso`) | **Solo ve analítica y lectura**. Prohibido visualizar acciones de escritura operativa ("Actas", "Evidencias", "Operación Electoral"). |
| **`MASTER`** | **Principal**<br>**Actas**<br>**Evidencias**<br>**Operación Electoral**<br>**Configuración**<br>**Administración** | • Dashboard (`/dashboard`)<br>• Aprobar / Rechazar (`/coordinator/actas`)<br>• Reportes CNE (`/coordinator/reportes`)<br>• Subir evidencia (`/coordinator/actas`)<br>• Mis JRVs (`/observer/mis-jrvs`)<br>• Mis Actas (`/observer/mis-actas`)<br>• Catálogos Electorales (`/master/configuracion`)<br>• Gestión de usuarios (`/admin/usuarios`)<br>• Sesiones activas (`/admin/sesiones`) | **Taxonomía completa consolidada con CERO duplicados**. Submenús de Cargos, Partidos y Candidatos mapeados canónicamente a `/master/configuracion`. Cero doble inyección de "Mis JRVs". |

---

## Por qué

1. **Defecto Crítico de Duplicación en Runtime (Doble Inyección en `SidebarComponent L101-113`)**:
   En el código actual de [`SidebarComponent`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/layout/sidebar/sidebar.component.ts), tras procesar los nodos devueltos por el backend mediante `mapNodesToSections`, se ejecuta el siguiente bloque imperativo:
   ```typescript
   // Agregar sección de operación electoral solo a roles con acceso al flujo (OBSERVER y MASTER)
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
   Debido a que el mapper ya procesaba previamente la sección "Actas" con "Registrar Acta" mapeado a `/observer/mis-jrvs` (o los seeds incluían ya nodos electorales), esta inyección incondicional forzada insertaba una segunda sección "Operación Electoral" al final del árbol con la misma ruta `/observer/mis-jrvs`. Para el rol `MASTER` (y situaciones con veedor), la ruta y sección aparecían duplicadas en el DOM, provocando confusión en el usuario y rompiendo el estándar de navegación limpia.

2. **Rutas Placeholder en Producción**:
   El seed legado de la base de datos (`changelog/014-menus-seed-aligned.sql`) contiene rutas como `/actas`, `/evidencias` no cualificado, `/admin` no cualificado, `/master/cargos`, `/master/partidos`, y `/master/candidatos` que en [`app.routes.ts`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/app.routes.ts) apuntan a [`PlaceholderPage`](file:///home/andy/Escritorio/PROYECTOS/CONTROLs/Control-s-Elector/frontend/app/src/app/features/placeholder/placeholder.page.ts). Los usuarios hacen clic en el sidebar y llegan a pantallas "En construcción". Todos los submenús de configuración electoral deben consolidarse bajo la ruta canónica `/master/configuracion`.

3. **Fragilidad de `mapRoute()`**:
   El sidebar intentaba parchar desalineaciones reescribiendo rutas en memoria (`/actas/nueva` -> `/observer/mis-jrvs`, `/actas/aprobar` -> `/coordinator/actas`). Esta lógica procedural es frágil, incompleta y susceptible a desfases frente a los guards de rutas.

4. **Inconsistencias en Contingencia Offline**:
   En recintos electorales con baja conectividad celular en la provincia de Santa Elena, una falla en `GET /api/v1/menus/me` provocaba que el sidebar recurriera a un fallback incompleto (`getFallbackSections()`) que no contemplaba todos los roles ni todas las rutas operativas (como `/observer/mis-actas` o `/coordinator/reportes`).

---

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Taxonomía Canónica Declarativa** | Definición de `NAV_CONFIG` con secciones, sub-ítems y rutas canónicas tipadas por rol sin duplicados | Modificación de esquemas DDL o seeds Liquibase en el backend |
| **Pipeline de Desduplicación Estricta** | Supresión definitiva del bloque L101-113 de `SidebarComponent` e incorporación de filtro desduplicador (Map/Set) por sección y ruta unívoca | Rediseño visual general del layout o cabecera `TopbarComponent` |
| **Mapeo Anti-Placeholder de Administración** | Mapeo canónico de submenús "Cargos Electorales", "Partidos Políticos" y "Candidatos" hacia `/master/configuracion` | Creación de formularios o pantallas adicionales fuera de las existentes |
| **Eliminación de Parches Procedimentales** | Supresión definitiva de `mapRoute()`, rutas placeholder en sidebar y bloques `push` dispersos | Eliminación física de `PlaceholderPage` en `app.routes.ts` (permanecen aisladas para rutas no vinculadas) |
| **Pruebas y Verificación Automatizada** | Suite de pruebas unitarias cubriendo los 4 roles (`OBSERVER`, `COORDINATOR`, `ADMIN`, `MASTER`), test de no-duplicación (0 duplicados en DOM), cero placeholders y resiliencia offline | Creación de endpoints de backend |

---

## Criterios de Aceptación

- [ ] **CA-1 (OBSERVER)**: Usuario con rol `OBSERVER` visualiza **únicamente** la sección "Operación Electoral" con `/observer/mis-jrvs` y `/observer/mis-actas`. No se visualiza "Principal" (Dashboard), "Actas" ("Aprobar / Rechazar"), "Evidencias" ni "Administración".
- [ ] **CA-2 (COORDINATOR)**: Usuario con rol `COORDINATOR` visualiza "Principal" (`/dashboard`), "Actas" (`/coordinator/actas`) y "Evidencias" (`/coordinator/actas`). No visualiza "Registrar Acta", "Operación Electoral" ni "Administración".
- [ ] **CA-3 (ADMIN)**: Usuario con rol `ADMIN` visualiza "Principal" (`/dashboard`, `/dashboard/resultados`, `/dashboard/progreso`). No ve ninguna acción operativa de escritura ni secciones "Actas", "Evidencias" u "Operación Electoral".
- [ ] **CA-4 (MASTER)**: Usuario con rol `MASTER` visualiza la taxonomía completa consolidada con **CERO duplicados**, incluyendo "Principal", "Actas", "Evidencias", "Operación Electoral", "Configuración" (`/master/configuracion`) y "Administración" (`/admin/usuarios`, `/admin/sesiones`).
- [ ] **CA-5 (Garantía de No-Duplicación)**: Ninguna sección ni ruta se renderiza más de una vez en el DOM del sidebar. Se erradica por completo la doble inyección de "Operación Electoral - Mis JRVs" observada en runtime.
- [ ] **CA-6 (Mapeo Anti-Placeholder de Administración)**: Ningún enlace en el DOM del sidebar apunta a `/actas`, `/evidencias` no cualificado, `/admin` crudo, ni `/master/cargos` / `/master/partidos` / `/master/candidatos`. Los catálogos electorales apuntan a `/master/configuracion`.
- [ ] **CA-7 (Sin mapRoute ni Inyecciones Procedurales)**: Se remueven definitivamente la función procedural `mapRoute()` y el bloque `push` en L101-113 de `SidebarComponent`.
- [ ] **CA-8 (Resiliencia Offline y Cero Parpadeo)**: Si `GET /api/v1/menus/me` falla con HTTP 500, timeout o error de red (offline), el sidebar se dibuja inmediatamente desde `NAV_CONFIG` correspondiente al rol en `AuthStore` sin parpadeos ni estados vacíos.
- [ ] **CA-9 (Suite de Pruebas Unitarias)**: `sidebar.component.spec.ts` cuenta con cobertura del 100% de los 4 roles, test de no-duplicación, test de cero placeholders y test de resiliencia ante error HTTP 500.

---

## Fuera de Alcance

- Migraciones de base de datos o cambios en `MenuController` de Spring Boot.
- Implementación funcional de las pantallas de destino (cubiertas por sus respectivos changes: `T-FE-15`, `T-FE-16`, `T-FE-17`).
- Modificación de las rutas y guards de `app.routes.ts`.

---

## Dependencias

- **`T-FE-02`**: Routing con RBAC Guards (archivado).
- **`T-FE-03`**: AuthService y AuthStore (activo, provee `store.user` con señal reactiva del rol).
- **`T-FE-06`**: Observer Mis JRVs y Mis Actas (rutas `/observer/mis-jrvs` y `/observer/mis-actas`).
- **`T-FE-16`**: Configuración de Catálogos Electorales (ruta consolidada `/master/configuracion`).
