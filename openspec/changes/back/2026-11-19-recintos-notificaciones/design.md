# Design — Recintos & Notificaciones de Actas Pendientes

---

## Schema de Base de Datos

### Tabla: recinto

```sql
CREATE TABLE recinto (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  nombre VARCHAR(255) NOT NULL,
  parroquia_id BIGINT NOT NULL REFERENCES parroquia(id),
  coordinator_id BIGINT REFERENCES usuario(id),
  estado VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL,
  fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  
  CONSTRAINT uk_recinto_nombre_parroquia UNIQUE (nombre, parroquia_id),
  CONSTRAINT fk_recinto_parroquia FOREIGN KEY (parroquia_id) REFERENCES parroquia(id),
  CONSTRAINT fk_recinto_coordinator FOREIGN KEY (coordinator_id) REFERENCES usuario(id)
);

CREATE INDEX idx_recinto_parroquia_id ON recinto(parroquia_id);
CREATE INDEX idx_recinto_coordinator_id ON recinto(coordinator_id);
CREATE INDEX idx_recinto_estado ON recinto(estado);
```

### Modify Tabla: jrv

```sql
ALTER TABLE jrv ADD COLUMN recinto_id BIGINT REFERENCES recinto(id);
CREATE INDEX idx_jrv_recinto_id ON jrv(recinto_id);

-- Notas:
-- - Inicialmente nullable (para datos existentes)
-- - En migration script: asignar JRVs a Recintos basado en parroquia
-- - Luego fazer NOT NULL (una vez migrados)
```

### Modify Tabla: acta

```sql
ALTER TABLE acta ADD COLUMN aprobada_por BIGINT REFERENCES usuario(id);
ALTER TABLE acta ADD COLUMN fecha_aprobacion TIMESTAMP;
ALTER TABLE acta ADD COLUMN motivo_rechazo VARCHAR(500);

CREATE INDEX idx_acta_aprobada_por ON acta(aprobada_por);
CREATE INDEX idx_acta_estado_recinto ON acta(estado, (SELECT recinto_id FROM jrv WHERE jrv.id = acta.jrv_id));
```

### Nueva Tabla: notificacion

```sql
CREATE TABLE notificacion (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id),
  tipo VARCHAR(50) NOT NULL, -- ACTA_ENVIADA, ACTA_APROBADA, ACTA_RECHAZADA
  acta_id BIGINT REFERENCES acta(id),
  titulo VARCHAR(255) NOT NULL,
  mensaje TEXT NOT NULL,
  leida BOOLEAN DEFAULT FALSE,
  fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  
  CONSTRAINT fk_notif_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
  CONSTRAINT fk_notif_acta FOREIGN KEY (acta_id) REFERENCES acta(id)
);

CREATE INDEX idx_notif_usuario_id ON notificacion(usuario_id);
CREATE INDEX idx_notif_leida ON notificacion(leida);
CREATE INDEX idx_notif_fecha ON notificacion(fecha_creacion DESC);
CREATE INDEX idx_notif_usuario_leida ON notificacion(usuario_id, leida);
```

---

## API Endpoints

### Recintos (CRUD)

#### GET /api/v1/admin/recintos
```json
Request: (ninguno)
Response (200):
{
  "data": [
    {
      "id": 1,
      "nombre": "Escuela República del Ecuador",
      "parroquia": "Centro, Pichincha",
      "coordinador": {
        "id": 10,
        "nombre": "Juan Pérez",
        "email": "j.perez@control.ec"
      },
      "totalMesas": 5,
      "estado": "ACTIVO",
      "fechaCreacion": "2026-11-01T10:00:00Z"
    }
  ],
  "total": 15,
  "page": 1
}
```

#### POST /api/v1/admin/recintos
```json
Request (MASTER only):
{
  "nombre": "Escuela Nueva",
  "parroquiaId": 42,
  "coordinadorId": 10,
  "estado": "ACTIVO"
}

Response (201):
{
  "id": 2,
  "nombre": "Escuela Nueva",
  "parroquia": "Centro, Pichincha",
  "coordinador": { ... },
  "estado": "ACTIVO"
}

Errors:
  400 Bad Request — nombre + parroquiaId duplicados
  404 Not Found — parroquiaId inválida
  404 Not Found — coordinadorId inválida o no tiene rol COORDINATOR
```

#### PUT /api/v1/admin/recintos/{id}
```json
Request (MASTER only):
{
  "nombre": "Escuela Actualizada",
  "coordinadorId": 11,
  "estado": "INACTIVO"
}

Response (200): Recinto actualizado
```

#### DELETE /api/v1/admin/recintos/{id}
```
Response (204): Recinto eliminado

Errors:
  400 Bad Request — Recinto tiene JRVs asignadas (no puede borrar)
  404 Not Found — Recinto no existe
```

---

### Actas Pendientes (Coordinator)

#### GET /api/v1/coordinator/actas-pendientes?estado=ENVIADA&limit=20&offset=0
```json
Response (200):
{
  "data": [
    {
      "actaId": 100,
      "jrvNumero": "001",
      "jrvNombre": "Escuela X - Mesa #1",
      "cargo": "Presidente",
      "observer": {
        "id": 50,
        "nombre": "María González"
      },
      "estado": "ENVIADA",
      "totalSufragantes": 234,
      "fechaRegistro": "2026-11-19T14:30:00Z",
      "motivoRechazo": null,
      "accionesDisponibles": ["aprobar", "rechazar", "ver_detalle"]
    }
  ],
  "total": 5,
  "pendientesCount": 5
}

Autenticación: JWT (COORDINATOR)
RLS: Solo actas de Recintos asignados al usuario
```

#### PUT /api/v1/coordinator/actas/{actaId}/aprobar
```json
Request:
{
  "comentario": "(opcional) Comentario interno"
}

Response (200):
{
  "actaId": 100,
  "estado": "APROBADA",
  "aprobadaPor": "Juan Pérez",
  "fechaAprobacion": "2026-11-19T15:00:00Z"
}

Side-effects:
  - Crear notificación para Observer: "Tu acta ha sido APROBADA"
  - Actualizar tabla acta: estado = APROBADA, aprobada_por = {userId}, fecha_aprobacion = NOW
```

#### PUT /api/v1/coordinator/actas/{actaId}/rechazar
```json
Request:
{
  "motivoRechazo": "Foto borrosa, no se ve el acta claramente"
}

Response (200):
{
  "actaId": 100,
  "estado": "EN_DISPUTA",
  "motivoRechazo": "Foto borrosa...",
  "coordinador": "Juan Pérez"
}

Side-effects:
  - Crear notificación para Observer: "Tu acta fue RECHAZADA. Motivo: {motivoRechazo}"
  - Actualizar tabla acta: estado = EN_DISPUTA, motivo_rechazo = {texto}
```

#### GET /api/v1/coordinator/actas/{actaId}/detalle
```json
Response (200):
{
  "actaId": 100,
  "jrv": { "id": 1, "numero": "001", "parroquia": "Centro" },
  "cargo": "Presidente",
  "observer": "María González",
  "estado": "ENVIADA",
  "fechaRegistro": "2026-11-19T14:30:00Z",
  
  "totalSufragantes": 234,
  "votosBlancos": 12,
  "votosNulos": 5,
  
  "urlFoto": "s3://buckets/actas/acta-100.webp",
  "fotoMetadata": {
    "uploadedAt": "2026-11-19T14:30:00Z",
    "size": 102400,
    "compressed": true
  },
  
  "detalles": [
    {
      "candidatoId": 1,
      "nombre": "Candidato A",
      "orden": 1,
      "votos": 89,
      "partido": "Partido A"
    }
  ]
}
```

---

### Notificaciones

#### GET /api/v1/notifications/pendientes?limit=10
```json
Response (200):
{
  "data": [
    {
      "id": 500,
      "tipo": "ACTA_ENVIADA",
      "titulo": "Nueva acta pendiente de revisión",
      "mensaje": "Mesa 001 - Presidente (Observer: María González)",
      "actaId": 100,
      "leida": false,
      "fechaCreacion": "2026-11-19T14:30:00Z"
    }
  ],
  "sinLeer": 5
}

Autenticación: JWT
```

#### PUT /api/v1/notifications/{notifId}/leer
```
Response (204): Notificación marcada como leída
```

---

## Validaciones Backend

### RecintoService
```
✓ nombre + parroquiaId → único (UK constraint)
✓ coordinadorId (si se proporciona) → usuario existe + rol=COORDINATOR
✓ parroquiaId → parroquia existe
✓ estado → enum(ACTIVO, INACTIVO)
✓ No eliminar si recinto tiene JRVs asignadas
```

### ActaService (modificado)
```
✓ Cuando acta → ENVIADA:
  - Obtener recinto_id de JRV
  - Crear notificación para Coordinator del Recinto
  - Enviar push (si está en la app)
  
✓ Cuando acta → APROBADA:
  - Validar usuario es Coordinator del Recinto
  - Crear notificación para Observer
  
✓ Cuando acta → EN_DISPUTA:
  - motivo_rechazo requerido (1-500 chars)
  - Crear notificación para Observer
```

---

## Row-Level Security (RLS)

### Para COORDINATOR

Acta es visible si:
```sql
EXISTS (
  SELECT 1 FROM recinto r
  INNER JOIN jrv j ON j.recinto_id = r.id
  WHERE j.id = acta.jrv_id
  AND r.coordinator_id = current_user_id
)
```

### Para OBSERVER

Acta es visible/editable si:
```sql
acta.registrada_por = current_user_id
```

---

## Frontend Architecture

### Components

1. **recintos-list.page.ts** (Admin)
   - Tabla de recintos con columnas: nombre, parroquia, coordinador, # mesas, estado
   - Buttons: Crear, Editar, Eliminar, Ver JRVs

2. **recinto-form.modal.ts**
   - Provincia → Cantón → Parroquia (cascada)
   - Nombre: [input]
   - Coordinador: [dropdown de usuarios COORDINATOR]
   - [Guardar] [Cancelar]

3. **actas-pendientes.page.ts** (Coordinator)
   - Tabs: Pendientes (ENVIADA) | Revisadas (APROBADA/EN_DISPUTA)
   - Listado con cards: JRV, Cargo, Observer, Fecha, [Acciones]
   - Polling cada 5s: `GET /coordinator/actas-pendientes`

4. **acta-detalle.modal.ts**
   - Foto (lightbox)
   - Datos: sufragantes, blancos, nulos
   - Tabla: candidatos + votos
   - Buttons: [Aprobar] [Rechazar]

5. **rechazo-form.modal.ts**
   - TextArea: motivo (max 500 chars)
   - [Rechazar] [Cancelar]

6. **notificaciones.component.ts** (Bell icon)
   - Dropdown con últimas 5 notificaciones
   - Contador de sin leer
   - Click → navega a acta-detalle

---

## Testing Strategy

### Backend Unit Tests

- **RecintoServiceTest**
  - Crear recinto válido ✓
  - Duplicado nombre+parroquia → error
  - Coordinador inválido → error
  - Eliminar con JRVs asignadas → error

- **ActaServiceTest (modificado)**
  - Acta → ENVIADA → notificación creada ✓
  - Acta → APROBADA → notificación a Observer ✓
  - Motivo rechazo requerido si EN_DISPUTA ✓

### Backend Integration Tests

- POST /admin/recintos → 201 + notificación a Coordinator
- GET /coordinator/actas-pendientes → RLS: solo sus recintos
- PUT /coordinator/actas/{id}/aprobar → 200 + notif a Observer
- PUT /coordinator/actas/{id}/rechazar → 200 + motivo + notif

### Frontend Tests

- recintos-list: cargar, crear, editar, eliminar (con validación)
- actas-pendientes: polling cada 5s, mostrar actas, aprobar, rechazar
- notificaciones: mostrar badge, click abre modal

### E2E Tests (Optativo Phase 2)

- Observer sube acta → Coordinator ve notificación → Approba → Observer ve acta aprobada
- Coordinator rechaza → Observer recibe notificación + puede corregir

---

## Migraciones

### Migration 1: Crear Recinto + JRV.recinto_id

```sql
-- Crear tabla recinto (ver schema arriba)

-- Agregar columna a jrv
ALTER TABLE jrv ADD COLUMN recinto_id BIGINT;

-- Asignar JRVs existentes a Recintos
-- Lógica: Por cada parroquia, crear 1 Recinto "Parroquia X - Jornada 2026"
-- Luego asignar JRVs de esa parroquia a ese Recinto

INSERT INTO recinto (nombre, parroquia_id, estado)
SELECT DISTINCT 
  CONCAT(p.nombre, ' - Jornada 2026'),
  p.id,
  'ACTIVO'
FROM parroquia p
WHERE EXISTS (SELECT 1 FROM jrv j WHERE j.parroquia_id = p.id);

UPDATE jrv j
SET recinto_id = (
  SELECT r.id FROM recinto r
  WHERE r.parroquia_id = j.parroquia_id
  LIMIT 1
);

-- Después: ALTER TABLE jrv ALTER COLUMN recinto_id SET NOT NULL;
```

### Migration 2: Acta + Notificacion

```sql
-- Agregar columnas a acta (ver schema)
-- Crear tabla notificacion (ver schema)
```

---

## Rollout & Rollback

### Phase 0: Preparar
- Merge migraciones
- Verificar JRVs asignadas a Recintos

### Phase 1: Backend (endpoints + RLS)
- RecintoService + Controller
- ActaService (modificado con notificaciones)
- NotificacionService

### Phase 2: Frontend
- recintos-list (Admin)
- actas-pendientes (Coordinator)
- notificaciones (Bell)

### Phase 3: Deploy
- Asignar Coordinators a Recintos (manual o endpoint)

### Rollback
- Revert migraciones (JRV.recinto_id → nullable)
- Endpoints retornan 404 (feature flag optional)

