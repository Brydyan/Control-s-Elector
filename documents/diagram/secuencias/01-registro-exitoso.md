# Flujo 01 — Registro de Acta Exitoso (Happy Path Online)

**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).

Escenario nominal: observador con conexión activa registra su acta correctamente.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SVC as ActaService
    participant STG as Supabase Storage
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    OBS->>APP: Selecciona Cantón y JRV (cualquiera)
    APP->>API: GET /api/jrv/{jrv_id}/cargos
    API->>JPA: findCargosByJrv(jrv_id)
    JPA->>PG: SELECT cargos via jrv_cargo WHERE jrv_id = ?
    PG-->>JPA: [Prefecto, Alcalde, Concejal Urbano, ...]
    JPA-->>API: [CargoDTO]
    API-->>APP: Lista de cargos aplicables a esa JRV

    OBS->>APP: Selecciona cargo (ej: Alcalde)
    APP->>API: GET /api/cargos/{cargo_id}/candidatos
    API->>JPA: findCandidatosByCargo(cargo_id)
    JPA->>PG: SELECT candidatos JOIN partidos WHERE cargo_id = ?
    PG-->>JPA: [CandidatoDTO {id, nombre, partido, numero_lista}]
    API-->>APP: Lista de candidatos con partido y número

    OBS->>APP: Ingresa votos por candidato + nulos + blancos + total
    OBS->>APP: Captura foto del acta física
    APP->>APP: Comprime imagen a WebP (~350KB)

    APP->>API: POST /api/actas {jrv_id, cargo_id, detalle:[{candidato_id, votos}], nulos, blancos, total_sufragantes, webp}
    API->>API: Valida JWT y rol OBSERVER
    API->>SVC: procesarActa(dto, imagen)

    SVC->>SVC: Valida SUM(detalle.votos) + nulos + blancos == total_sufragantes ✅
    SVC->>SVC: Valida total_sufragantes <= jrv.total_electores ✅

    SVC->>STG: upload imagen.webp (bucket: evidencias)
    STG-->>SVC: storage_url

    SVC->>JPA: save(Acta {jrv_id, cargo_id, storage_url, estado: ENVIADA})
    JPA->>PG: INSERT acta
    PG-->>JPA: acta_id

    SVC->>JPA: saveAll(ActaDetalle [{acta_id, candidato_id, votos}])
    JPA->>PG: INSERT acta_detalle (bulk)
    PG-->>JPA: ok

    JPA-->>SVC: Acta persistida
    SVC-->>API: ActaResponseDTO
    API-->>APP: 201 Created {acta_id}
    APP-->>OBS: ✅ Acta registrada exitosamente
```
