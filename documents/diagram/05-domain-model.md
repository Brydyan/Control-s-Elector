# Diagrama 05 — Modelo de Dominio Electoral

Entidades del negocio para las Elecciones Seccionales 2027-2031, Provincia de Santa Elena.

```mermaid
erDiagram
    PROVINCIA {
        uuid id PK
        string nombre
        string codigo
    }

    CANTON {
        uuid id PK
        uuid provincia_id FK
        string nombre
        string codigo
    }

    PARROQUIA {
        uuid id PK
        uuid canton_id FK
        string nombre
        boolean es_rural
    }

    RECINTO_ELECTORAL {
        uuid id PK
        uuid parroquia_id FK
        string nombre
        string direccion
    }

    JRV {
        uuid id PK
        uuid recinto_id FK
        int numero_junta
        int total_electores
    }

    USUARIO {
        uuid id PK
        string cedula
        string nombre_completo
        string email
        string rol
    }


    CARGO {
        uuid id PK
        string nombre
        string nivel_geografico
    }

    PARTIDO {
        uuid id PK
        string nombre
        string siglas
        int numero
        string color_hex
    }

    CANDIDATO {
        uuid id PK
        uuid cargo_id FK
        uuid partido_id FK
        string nombre
        string apellido
        int numero_lista
    }

    JRV_CARGO {
        uuid id PK
        uuid jrv_id FK
        uuid cargo_id FK
    }

    ACTA {
        uuid id PK
        uuid jrv_id FK
        uuid cargo_id FK
        uuid registrada_por FK
        uuid aprobada_por FK
        int votos_nulos
        int votos_blancos
        int total_sufragantes
        string estado
        string motivo_disputa
        boolean requirio_coordinador
        string storage_url
        timestamp registrada_en
        timestamp aprobada_en
        int version
    }

    ACTA_DETALLE {
        uuid id PK
        uuid acta_id FK
        uuid candidato_id FK
        int votos
    }

    AUDIT_LOG {
        uuid id PK
        uuid user_id FK
        uuid jrv_id FK
        uuid acta_id FK
        string accion
        jsonb payload
        timestamp timestamp
    }

    NOTIFICACION {
        uuid id PK
        uuid observer_id FK
        uuid acta_id FK
        string tipo
        boolean leida
        timestamp creada_en
    }

    REFRESH_TOKEN {
        uuid id PK
        uuid user_id FK
        string token_hash
        timestamp expires_at
        timestamp created_at
    }

    PROVINCIA ||--o{ CANTON : "tiene"
    CANTON ||--o{ PARROQUIA : "tiene"
    PARROQUIA ||--o{ RECINTO_ELECTORAL : "contiene"
    RECINTO_ELECTORAL ||--o{ JRV : "alberga"
    JRV ||--o{ JRV_CARGO : "aplica"
    CARGO ||--o{ JRV_CARGO : "aplica en"
    CARGO ||--o{ CANDIDATO : "tiene"
    PARTIDO ||--o{ CANDIDATO : "postula"
    JRV ||--o{ ACTA : "genera"
    CARGO ||--o{ ACTA : "corresponde a"
    USUARIO ||--o{ ACTA : "registra"
    USUARIO ||--o{ ACTA : "aprueba"
    ACTA ||--o{ ACTA_DETALLE : "desglosa"
    CANDIDATO ||--o{ ACTA_DETALLE : "recibe votos"
    USUARIO ||--o{ AUDIT_LOG : "genera"
    ACTA ||--o{ AUDIT_LOG : "registra"
    ACTA ||--o{ NOTIFICACION : "genera"
    USUARIO ||--o{ NOTIFICACION : "recibe"
    USUARIO ||--o{ REFRESH_TOKEN : "tiene"
```

---

## Cargos — Elecciones Seccionales 2027-2031

| Cargo | Nivel Geográfico | Aplica en |
|-------|-----------------|-----------|
| Prefecto/a | `PROVINCIA` | Todas las JRVs |
| Consejero/a CPCCS | `NACIONAL` | Todas las JRVs |
| Alcalde/sa | `CANTON` | Todas las JRVs del cantón |
| Concejal Urbano | `CANTON` | JRVs en zonas urbanas |
| Concejal Rural | `CANTON` | JRVs en zonas rurales |
| Vocal Junta Parroquial Rural | `PARROQUIA` | JRVs en parroquias rurales únicamente |

> `JRV_CARGO` define qué cargos aplican para cada JRV según su ubicación geográfica.

---

## Regla de Validación por Acta

Cada acta corresponde a **un cargo**. La validación aplica de forma independiente por cargo:

```
SUM(acta_detalle.votos WHERE acta_id = ?) + votos_nulos + votos_blancos = total_sufragantes
```

---

## Estados del Acta

| Estado | Quién lo asigna | Descripción |
|--------|----------------|-------------|
| `BORRADOR` | OBSERVER | Formulario en edición, no enviado |
| `ENVIADA` | OBSERVER | Subida exitosa, pendiente revisión |
| `EN_DISPUTA` | COORDINATOR / MASTER | Foto no coincide con datos digitados |
| `CORREGIDA` | OBSERVER / COORDINATOR | Datos corregidos tras disputa |
| `APROBADA` | COORDINATOR / MASTER | Datos verificados y confirmados |
| `ANULADA` | MASTER | Fraude o irregularidad grave — estado final |

---

## Roles de Usuario

| Rol | Descripción |
|-----|-------------|
| `MASTER` | Acceso total — registra cargos, partidos, candidatos y puede anular actas |
| `COORDINATOR` | Global operativo — gestiona disputas, correcciones y aprobaciones |
| `ADMIN` | Global solo lectura — dashboard y estadísticas en tiempo real |
| `OBSERVER` | Registra actas (selecciona JRV + Cantón libremente) y las corrige si COORDINATOR lo solicita |

---

## Acciones de AUDIT_LOG

| Acción | Generada por |
|--------|-------------|
| `VALIDACION_FALLIDA` | OBSERVER — totales incorrectos rechazados por API |
| `STORAGE_REINTENTOS_AGOTADOS` | OBSERVER — upload imagen falló 3 veces |
| `ACTA_EN_DISPUTA` | COORDINATOR / MASTER |
| `ACTA_CORREGIDA_POR_OBSERVER` | OBSERVER |
| `ACTA_CORREGIDA_POR_COORDINATOR` | COORDINATOR |
| `ACTA_APROBADA` | COORDINATOR / MASTER |
| `ACTA_ANULADA` | MASTER |

---

## Tipos de NOTIFICACION

| Tipo | Receptor | Cuándo |
|------|----------|--------|
| `CORRECCION_REQUERIDA` | OBSERVER | COORDINATOR marca acta EN_DISPUTA |
| `ACTA_LISTA_PARA_REVISION` | COORDINATOR | OBSERVER corrige acta |
| `ACTA_ANULADA` | COORDINATOR | MASTER anula acta APROBADA |

---

## Cantones de Santa Elena

| Cantón | Código | JRV Estimadas |
|--------|--------|---------------|
| Santa Elena | 2401 | ~200 |
| La Libertad | 2402 | ~150 |
| Salinas | 2403 | ~100 |
