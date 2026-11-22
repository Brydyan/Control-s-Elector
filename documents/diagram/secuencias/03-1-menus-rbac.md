# Flujo 03.1 — Construcción de Sidebar RBAC (GET /api/v1/menus/me)

Escenario: Post-login, FE obtiene arbol de menus visibles según los permisos del usuario.

```mermaid
sequenceDiagram
    actor USER as Usuario Autenticado
    participant APP as App Angular
    participant API as Quarkus API
    participant MENUSVC as MenuService
    participant PG as PostgreSQL

    USER->>APP: Login exitoso → redirect a /observer o /dashboard
    APP->>API: GET /api/v1/menus/me<br/>(Authorization: Bearer access_token)

    API->>API: JwtAuthFilter valida token<br/>Extrae userId, perms[] del JWT
    API->>MENUSVC: getMyMenus(userId)

    MENUSVC->>PG: SELECT * FROM menu WHERE activo = true
    PG-->>MENUSVC: Lista completa ~20-50 menus
    MENUSVC->>PG: SELECT menu_id, authority FROM menu_permiso
    PG-->>MENUSVC: Mapa {menuId → [auth1, auth2, ...]}
    MENUSVC->>PG: SELECT authority FROM rol_permiso<br/>WHERE usuario_id = ?
    PG-->>MENUSVC: permisos efectivos del usuario

    alt Usuario tiene permisos
        MENUSVC->>MENUSVC: Regla 1: menus directamente visibles<br/>- Dashboard: siempre visible (público)<br/>- Otros: si usuario tiene ≥1 permiso del menu<br/>Regla 2: Walk-up ancestros faltantes<br/>Regla 3: Excluir menus sin permisos + no públicos
        MENUSVC->>MENUSVC: Construir árbol jerárquico ordenado
        MENUSVC-->>API: MenuResponse {menus: [...]}

        API-->>APP: 200 OK {menus: [{publicId, codigo,<br/>label, icono, ruta, orden, children[]}]}

        APP->>APP: Renderiza sidebar con menus visibles
        APP-->>USER: ✅ Sidebar completo según rol

    else Usuario sin permisos relevantes
        MENUSVC-->>API: MenuResponse {menus: [dashboard]}
        API-->>APP: 200 OK solo dashboard visible
        APP-->>USER: ⚠️ Sidebar vacío excepto dashboard
    end
```

---

## Reglas de Visibilidad de Menus

| Regla | Descripción |
|-------|-------------|
| **Público** | Menu código en `['dashboard']` → siempre visible (no requiere permiso) |
| **Permiso requerido** | Menu tiene ≥1 `menu_permiso` → usuario debe tener ≥1 permiso match (OR) |
| **Sin permiso + no público** | Menu excluido aunque sea hijo de padre visible |
| **Walk-up** | Ancestros de menus visibles incluidos siempre (mantiene estructura visual) |

---

## Cardinalidad

- **Menus totales:** ~20-50
- **Cargados en memoria:** Sí (toda el catalogo, filtrado post-query)
- **Típica complejidad O(n):** Dos passes (directo + walk-up)

