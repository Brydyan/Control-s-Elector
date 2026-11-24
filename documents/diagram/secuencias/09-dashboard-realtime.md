# Flujo 09 — Dashboard: Actualización en Tiempo Real

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
Escenario: el dashboard recibe y muestra nuevas actas conforme los observadores las registran.

```mermaid
sequenceDiagram
    actor ADMIN_U as Admin / Coordinator / Master
    participant DASH as Dashboard Angular
    participant API as Quarkus API
    participant WS as WebSocket (mecanismo por definir)
    participant PG as PostgreSQL

    ADMIN_U->>DASH: Accede a resultados.brydyan.me
    DASH->>API: POST /api/auth/login → Bearer JWT
    API-->>DASH: JWT {rol: ADMIN}

    Note over DASH,API: Carga inicial de resultados

    DASH->>API: GET /api/resultados/consolidado + Bearer JWT
    API->>PG: SELECT consolidado por cantón y cargo
    PG-->>API: {santa_elena: {alcalde: 1240, prefecto: 980}, ...}
    API-->>DASH: ResultadosDTO
    DASH->>DASH: Renderiza totales iniciales por cargo y cantón

    Note over DASH,WS: Suscripción a cambios en tiempo real

    DASH->>WS: subscribe("actas", INSERT | UPDATE) + Bearer JWT
    WS-->>DASH: ✅ Canal suscrito

    Note over WS,PG: Un observador registra nueva acta

    PG->>WS: NOTIFY actas (nuevo INSERT)
    WS->>DASH: Evento {tipo: INSERT, payload: {acta_id, jrv_id, cargo_id, totales}}

    DASH->>DASH: Actualiza conteo local del cantón y cargo afectados
    DASH->>DASH: Recalcula porcentajes y progreso
    DASH->>ADMIN_U: Re-render reactivo (sin recarga de página)

    Note over DASH,ADMIN_U: El cambio es visible en menos de 2 segundos
```
