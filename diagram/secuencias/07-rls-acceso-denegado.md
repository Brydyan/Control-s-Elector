# Flujo 07 — Acceso Denegado por RLS (Acta de Otro OBSERVER)

**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).

Escenario: un OBSERVER intenta acceder a acta registrada por otro OBSERVER.

```mermaid
sequenceDiagram
    actor OBS1 as Observador Juan (registró: 5 actas)
    actor OBS2 as Observador María (registró: 3 actas)
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SEC as Spring Security
    participant PG as PostgreSQL (RLS activo)

    OBS1->>APP: Intenta ver acta registrada por María
    APP->>API: GET /api/actas/123 + Bearer {jwt: user_id=juan_id}

    API->>SEC: Valida JWT
    SEC-->>API: Autorizado (rol OBSERVER, user_id=juan_id)

    API->>PG: SELECT * FROM actas WHERE id = 123

    Note over PG: RLS Policy activa:<br/>WHERE registrada_por = current_user_id<br/>(juan_id)<br/>Acta 123 fue registrada por María<br/>(maria_id) → fila invisible

    PG-->>API: [] (resultado vacío por RLS)
    API-->>APP: 404 Not Found

    APP-->>OBS1: ❌ Acta no encontrada

    Note over OBS1,PG: Juan no puede ver acta de María.<br/>RLS la hace invisible.<br/>COORDINATOR ve todas las actas.
```
