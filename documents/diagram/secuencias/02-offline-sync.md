# Flujo 02 — Registro Offline y Sincronización al Reconectar

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
Escenario: observador sin cobertura de red guarda el acta localmente y la sincroniza al recuperar conexión.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular
    participant IDB as IndexedDB (Cola Local)
    participant FS as Private Storage
    participant NET as Monitor de Red
    participant API as Quarkus API

    OBS->>APP: Completa formulario del acta (JRV + cargo + votos por candidato)
    APP->>APP: Comprime imagen a WebP

    APP->>NET: ¿Hay conexión?
    NET-->>APP: ❌ Sin red

    APP->>IDB: Guarda acta completa en cola offline
    IDB-->>APP: Encolada (id_local)
    APP->>FS: Guarda imagen como JRV-{id}_{fecha}_{obs_id}.webp
    FS-->>APP: Archivo persistido en directorio privado
    APP-->>OBS: ⚠️ Guardado localmente, pendiente de sync

    Note over NET: ...tiempo después...

    NET->>APP: ✅ Conexión recuperada
    APP->>IDB: Obtiene actas pendientes
    IDB-->>APP: [acta_1, acta_2, ...]

    loop Por cada acta encolada
        APP->>API: POST /api/actas {jrv_id, cargo_id, detalle:[{candidato_id, votos}], nulos, blancos, total_sufragantes, webp}
        API-->>APP: 201 Created {acta_id}
        APP->>IDB: Marca acta como sincronizada
        APP->>FS: Elimina archivo local (ya sincronizado)
    end

    APP-->>OBS: ✅ Todas las actas sincronizadas
```
