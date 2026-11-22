# Flujo 06 — Conflicto de Concurrencia en la Misma JRV y Cargo

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
Escenario: dos dispositivos intentan registrar el acta del mismo cargo en la misma JRV simultáneamente.

```mermaid
sequenceDiagram
    participant APP_A as App Dispositivo A<br/>(Observador Titular)
    participant APP_B as App Dispositivo B<br/>(Observador Suplente)
    participant API as Quarkus API
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    par Envío simultáneo
        APP_A->>API: POST /api/actas {jrv_id: 42, cargo_id: 1, version: 0}
    and
        APP_B->>API: POST /api/actas {jrv_id: 42, cargo_id: 1, version: 0}
    end

    API->>JPA: save(Acta A) — Intenta bloquear fila JRV 42 + cargo 1
    JPA->>PG: SELECT ... FOR UPDATE WHERE jrv_id = 42 AND cargo_id = 1
    PG-->>JPA: Lock adquirido por A

    API->>JPA: save(Acta B) — Intenta bloquear misma fila
    JPA->>PG: SELECT ... FOR UPDATE WHERE jrv_id = 42 AND cargo_id = 1
    PG-->>JPA: Bloqueado, espera...

    JPA->>PG: UPDATE acta SET version = 1 (Acta A)
    PG-->>JPA: Committed, lock liberado
    JPA-->>API: Acta A persistida
    API-->>APP_A: 201 Created

    PG-->>JPA: Lock adquirido por B
    JPA->>PG: INSERT acta donde version = 0 (ya es 1)
    PG-->>JPA: OptimisticLockException (version mismatch)
    JPA-->>API: Conflicto de versión

    API-->>APP_B: 409 Conflict {code: JRV_CARGO_ALREADY_REGISTERED}
    APP_B-->>APP_B: Muestra acta existente para revisión
```
