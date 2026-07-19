# Flujo 13 — Configuración Pre-Electoral (MASTER)

n**Estado:** ⏳ PLANEADO — Código backend aún no implementado. Ver [diagram/README.md](../README.md).
Escenario: MASTER registra toda la información necesaria antes del día de votación.

---

## Parte A — Registro de Cargos, Partidos y Candidatos

```mermaid
sequenceDiagram
    actor MASTER as Master
    participant API as Spring Boot API
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    Note over MASTER,PG: Paso 1 — Registrar cargos electorales

    MASTER->>API: POST /api/cargos {nombre: Alcalde, nivel_geografico: CANTON}
    API->>JPA: save(Cargo)
    JPA->>PG: INSERT cargo
    PG-->>JPA: cargo_id
    API-->>MASTER: 201 Created {cargo_id}

    Note over MASTER: Repite para: Prefecto, Concejal Urbano,\nConcejal Rural, Vocal JPR, Consejero CPCCS

    Note over MASTER,PG: Paso 2 — Registrar partidos políticos

    MASTER->>API: POST /api/partidos {nombre, siglas, numero, color_hex}
    API->>JPA: save(Partido)
    JPA->>PG: INSERT partido
    PG-->>JPA: partido_id
    API-->>MASTER: 201 Created {partido_id}

    Note over MASTER: Repite para cada partido participante

    Note over MASTER,PG: Paso 3 — Registrar candidatos por cargo y partido

    MASTER->>API: POST /api/candidatos {cargo_id, partido_id, nombre, apellido, numero_lista}
    API->>JPA: save(Candidato)
    JPA->>PG: INSERT candidato
    PG-->>JPA: candidato_id
    API-->>MASTER: 201 Created {candidato_id}

    Note over MASTER: Repite para cada candidato\n(N candidatos × M cargos)
```

---

## Parte B — Asignación de Cargos por JRV

```mermaid
sequenceDiagram
    actor MASTER as Master
    participant API as Spring Boot API
    participant SVC as ConfiguracionService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    Note over MASTER,PG: Define qué cargos aplican para cada JRV\nsegún su ubicación geográfica

    MASTER->>API: POST /api/jrv/{jrv_id}/cargos {cargo_ids: [1,2,3,4]}
    API->>SVC: asignarCargos(jrv_id, cargo_ids)

    SVC->>JPA: saveAll(JrvCargo [{jrv_id, cargo_id}])
    JPA->>PG: INSERT jrv_cargo (bulk)
    PG-->>JPA: ok
    JPA-->>SVC: ok
    SVC-->>API: ok
    API-->>MASTER: 200 OK — cargos asignados a JRV

    Note over MASTER: JRVs en parroquias rurales → incluir Vocal JPR\nJRVs urbanas → excluir Vocal JPR
```

---

## Parte C — Creación de Usuarios OBSERVER

```mermaid
sequenceDiagram
    actor MASTER as Master
    participant API as Spring Boot API
    participant SVC as UsuarioService
    participant JPA as JPA Repository
    participant PG as PostgreSQL

    Note over MASTER,PG: Crear usuarios OBSERVER<br/>(realizado en reunión presencial pre-electoral)

    MASTER->>API: POST /api/usuarios {email, password, rol: OBSERVER}
    API->>SVC: crearUsuario(dto)
    SVC->>SVC: Valida email único
    SVC->>JPA: save(Usuario con rol=OBSERVER)
    JPA->>PG: INSERT usuario
    PG-->>JPA: user_id
    SVC-->>API: UsuarioDTO
    API-->>MASTER: 201 Created {user_id, email}

    Note over MASTER: Repite para cada OBSERVER<br/>(sin pre-asignación de JRVs)<br/>Cada OBSERVER registrará actas<br/>de cualquier JRV durante votación
```

---

## Checklist Pre-Electoral (orden obligatorio)

```
1. Registrar cargos electorales
2. Registrar partidos políticos
3. Registrar candidatos por cargo y partido
4. Asignar cargos a cada JRV según geografía (JRV_CARGO)
5. Crear usuarios OBSERVER
```

> El orden importa: candidatos dependen de cargos y partidos. JRV_CARGO debe estar poblada antes de que OBSERVERS registren actas.
