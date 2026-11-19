# SDD — Recintos & Notificaciones de Actas Pendientes

**Referencia:** T-COORD-RECINTOS-01  
**Epic:** Coordinator › Gestión de Recintos & Validación de Actas  
**Componentes:** Backend (Recinto entity + endpoints) + Frontend (Configuración > Recintos + Notificaciones)  

---

## Propuesta

Crear gestión de **Recintos** como unidad física que agrupa múltiples JRVs/Mesas, con asignación de **COORDINATOR** responsable. El Coordinador recibe notificaciones cuando un Observer sube acta y puede validarla.

**Hoy:** 
- JRV es la unidad mínima. No existe "Recinto" como entidad.
- Actas se envían pero no hay validación/aprobación.
- No existe notificación de actas pendientes.

**Mañana:**
- Recinto agrupa JRVs (una escuela = múltiples mesas)
- COORDINATOR asignado a Recinto recibe notificaciones
- Coordinator valida/rechaza actas en un panel centralizado
- Observer ve estado de sus actas (aprobada/rechazada/pendiente)

---

## Dominio de Negocio

### Jerarquía Geográfica (Corregida)

```
Provincia 
  → Cantón 
    → Parroquia
      → Recinto (edificio: escuela, colegio, etc)
         → JRV #1 (cuerpo administrativo)
         → JRV #2
         → JRV #3
            → Mesa #1 (donde trabaja JRV #1)
            → Mesa #2
            → Mesa #3
```

### Conceptos Aclarados

| Concepto | Def | Responsable |
|----------|-----|-------------|
| **JRV** | Órgano temporal: presidente, secretarios, vocales | Observer (carga acta) |
| **Mesa** | Unidad física donde trabaja la JRV | Observer (donde toma foto) |
| **Recinto** | Edificio que alberga múltiples JRVs/Mesas | COORDINATOR (valida actas) |
| **Acta** | Registro de votos de UNA JRV para UN cargo | Observer crea, Coordinator aprueba |

---

## Scope

### A. Nueva Entidad: Recinto

- **Tabla:** `recinto`
- **Campos:** 
  - `id` (PK)
  - `nombre` (Escuela X, Colegio Y)
  - `ubicacion_geografica` (referencia a parroquia)
  - `cantidad_mesas` (informativo, se calcula como COUNT(jrvs))
  - `coordinator_id` (FK a usuario, rol COORDINATOR)
  - `estado` (ACTIVO | INACTIVO)
  - `fecha_creacion`, `fecha_actualizacion`

- **Relaciones:**
  - Recinto ← N JRVs (una JRV pertenece a un Recinto)
  - Recinto → 1 COORDINATOR (asignación)

### B. Modificar JRV

- **Nueva columna:** `recinto_id` (FK a recinto, nullable temporalmente para migración)
- **Impacto:** JRV ya tiene locación (parroquia), ahora también tiene recinto explícito

### C. Panel de Recintos (Configuración)

**Ruta:** `/admin/configuracion/recintos` (en menú Configuración)

**Funcionalidad:**
- Listado de Recintos (tabla con: nombre, parroquia, coordinador asignado, estado)
- Botón "Crear Recinto" → Modal con:
  - Ubicación: Provincia → Cantón → Parroquia (cascada)
  - Nombre: [input]
  - Cantidad de Mesas: [calculado automático tras seleccionar JRVs]
  - Coordinador Asignado: [dropdown de usuarios con rol COORDINATOR]
  - [Crear] [Cancelar]
- Por cada Recinto: edit + delete (con validación: no si tiene JRVs asignadas)

### D. Panel de Actas Pendientes (Coordinator)

**Ruta:** `/coordinator/actas-pendientes` (en top bar, junto a campanita de notificaciones)

**Funcionalidad:**
- Listado de actas **ENVIADAS** del Recinto asignado
- Columnas: JRV (número), Cargo, Observer (nombre), Fecha, Estado, [Acciones]
- Acciones:
  - **Aprobar** → Estado APROBADA + notificación a Observer
  - **Rechazar** → Modal con motivo → Estado EN_DISPUTA + motivo guardado
  - **Ver Detalle** → Modal con foto + datos + candidatos/votos

### E. Notificaciones en Tiempo Real

**Push a COORDINATOR:**
1. Observer sube Acta → estado ENVIADA
2. Sistema genera notificación: "Acta pendiente: {jrv}/{cargo}/{observer}"
3. Aparece en:
   - Campanita (bell icon) del Coordinator
   - Lista `/coordinator/actas-pendientes`
   - Conteo badge: "5 actas pendientes"

**Push a OBSERVER (cuando acta se aprueba/rechaza):**
1. Coordinator aprueba → Acta → APROBADA
2. Observer recibe: "Tu acta ({jrv}/{cargo}) ha sido aprobada"
3. Si rechazada → "Tu acta fue rechazada por: {motivo}. Click para corregir"

### F. Estados de Acta (Existentes, se integran)

```
BORRADOR 
  ↓ (Observer envia)
ENVIADA 
  ├─ (Coordinator aprueba) → APROBADA
  └─ (Coordinator rechaza) → EN_DISPUTA
       ↓ (Observer corrige)
       CORREGIDA (re-envia a Coordinator)
         ├─ → APROBADA
         └─ → EN_DISPUTA (loop)
```

**Nuevos campos en Acta:**
- `aprobada_por` (Coordinator ID, si estado=APROBADA)
- `fecha_aprobacion`
- `motivo_rechazo` (si EN_DISPUTA)

---

## Endpoints Nuevos

### Recintos (CRUD)
- `GET /api/v1/admin/recintos` — listar todos
- `POST /api/v1/admin/recintos` — crear (MASTER only)
- `PUT /api/v1/admin/recintos/{id}` — editar (MASTER only)
- `DELETE /api/v1/admin/recintos/{id}` — eliminar (MASTER only)

### Actas Pendientes (Coordinator)
- `GET /api/v1/coordinator/actas-pendientes?estado=ENVIADA&recinto_id=X` — listar enviadas del recinto
- `PUT /api/v1/coordinator/actas/{actaId}/aprobar` — aprobar (COORDINATOR only)
- `PUT /api/v1/coordinator/actas/{actaId}/rechazar` — rechazar + motivo (COORDINATOR only)
- `GET /api/v1/coordinator/actas/{actaId}/detalle` — ver acta completa (foto + datos)

### Notificaciones
- `GET /api/v1/notifications/pendientes` — listar notificaciones del usuario
- `PUT /api/v1/notifications/{notifId}/leer` — marcar como leída
- `WebSocket /ws/notifications` — conexión en tiempo real (opcional Phase 2)

---

## Decisiones de Diseño

**D1 — ¿Recinto es obligatorio para JRV?**
- Opción A: SÍ. Toda JRV pertenece a un Recinto.
- Opción B: NO. JRV puede existir sin Recinto (backward compatible).
- **Elegida:** A. Recinto es concepto central. Migramos JRVs existentes a Recintos (1 Recinto genérico por parroquia si no hay claridad).

**D2 — ¿Un Coordinator puede gestionar múltiples Recintos?**
- Opción A: SÍ. Un Coordinator → muchos Recintos.
- Opción B: NO. Un Coordinator → un Recinto.
- **Elegida:** A. Es más flexible. Coordinator es responsable por zona.

**D3 — ¿Notificaciones en tiempo real (WebSocket)?**
- Opción A: SÍ. WebSocket para push inmediato.
- Opción B: NO. Polling cada N segundos.
- **Elegida:** B (MVP). Polling cada 5 segundos. Phase 2: WebSocket.

**D4 — ¿Coordinator puede ver actas de JRVs fuera su Recinto?**
- Opción A: SÍ. Es admin, ve todo.
- Opción B: NO. Solo su Recinto (RLS).
- **Elegida:** B. Row-level security: `WHERE recinto_id IN (SELECT id FROM recinto WHERE coordinator_id = current_user_id)`

**D5 — ¿Eliminar Recinto con JRVs asignadas?**
- Opción A: SÍ. Cascada: JRVs quedan sin Recinto.
- Opción B: NO. Bloquear si tiene JRVs.
- **Elegida:** B. Mantener integridad. Reasignar JRVs primero.

---

## Próximos Pasos

✅ Documento SDD listo en: `/openspec/changes/back/2026-11-19-recintos-notificaciones/`

→ **Pasar a Arquitecto para feedback en:**
- D1 (obligatoriedad de Recinto)
- D2 (Coordinator multi-recinto)
- D3 (notificaciones tiempo real vs polling)
- D4 (RLS para Coordinator)
- D5 (cascada vs bloqueo al eliminar)

**Once aprobado → Builder implementa Scope A-F**
