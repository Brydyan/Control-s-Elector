# 📋 Auditoría: Diagrama 05 vs Código Real

**Status:** ⚠️ CRÍTICA — Múltiples discrepancias entre ERD documentado y código JPA real.

Fecha: 2026-07-11

---

## 🔴 Discrepancias Críticas

### 1. USUARIO — Campos faltantes / diferentes

**Diagrama dice:**
```
USUARIO {
  uuid id PK
  string cedula
  string nombre_completo
  string email
  string rol
}
```

**Código real (Usuario.java):**
```java
@Entity
@Table(name = "usuario")
public class Usuario {
    Long id                           // BIGINT PK (autoincrement)
    UUID publicId                     // UUIDv7 (dual-key pattern)
    String email                      // UNIQUE
    String passwordHash               // BCrypt, NOT plaintext
    Rol rol                           // FK → rol table (NOT string)
    boolean habilitado                // NOT in diagram
    Instant creadoEn                  // Audit timestamp (NOT in diagram)
}
```

**Errores en diagrama:**
- ❌ `cedula` — NO EXISTS en código
- ❌ `nombre_completo` — NO EXISTS en código
- ❌ `string rol` — WRONG TYPE. Es FK a tabla ROL (entidad separada)
- ✅ `email` — OK
- ❌ Falta `passwordHash` — campo crítico para auth
- ❌ Falta `publicId` — UUIDv7 dual-key (PK es long, publicId es UUID)
- ❌ Falta `habilitado` — flag para soft-disable usuarios
- ❌ Falta `creadoEn` — audit timestamp

**Impacto:** ALTO. Diagrama dibuja usuario con datos (cedula, nombre_completo) que NO existen en BD. Login usa email, no cedula.

---

### 2. TABLAS NUEVAS EN CÓDIGO — NO DOCUMENTADAS EN DIAGRAMA

#### Tabla ROL
```java
@Entity
@Table(name = "rol")
public class Rol {
    Long id                          // PK
    String nombre                    // UNIQUE: MASTER, COORDINATOR, ADMIN, OBSERVER
    UUID publicId                    // UUIDv7 (dual-key)
    Instant creadoEn                 // Audit
}
```

**Relación:** `USUARIO.rol_id FK → ROL.id`

**En diagrama:** USUARIO tiene `string rol` (enum-like) — esto debería ser FK a ROL.

---

#### Tabla PERMISO
```java
@Entity
@Table(name = "permiso")
public class Permiso {
    Long id                          // PK
    UUID publicId                    // UUIDv7 (dual-key)
    String recurso                   // ej: "evidencia", "acta", "usuario"
    String accion                    // ej: "upload", "approve", "delete"
    String descripcion               // Nullable
}
```

**Unique constraint:** (recurso, accion) — clave canonica "evidencia:upload"

**En diagrama:** NOT MENTIONED. Diagrama no modelea permisos granulares (recurso:accion).

---

#### Tabla MENU
```java
@Entity
@Table(name = "menu")
public class Menu {
    Long id                          // PK
    UUID publicId                    // UUIDv7 (dual-key)
    Long parentId                    // FK (self-reference) — NULL si raiz
    String codigo                    // UNIQUE: i18n key (ej: "actas.aprobar")
    String label                     // Nombre visible
    String icono                     // Ionic/Material icon name (nullable)
    String ruta                      // Angular route (ej: "/actas/nueva")
    Integer orden                    // Sort order
    Boolean activo                   // Flag para excluir del sidebar
    Instant borradoEn                // Soft delete
}
```

**En diagrama:** NOT MENTIONED. Diagrama no modelea arbol de menus para sidebar RBAC.

---

#### Tabla REFRESH_TOKEN (PARCIALMENTE DOCUMENTADA)

**Diagrama dice:**
```
REFRESH_TOKEN {
  uuid id PK
  uuid user_id FK
  string token_hash
  timestamp expires_at
  timestamp created_at
}
```

**Código no explorado en detalle**, pero comentarios en AuthController indican:
- Token hash se guarda en DB
- TTL: 8h (duración proceso electoral)
- Persiste en PostgreSQL para revocación
- Viaja en cookie httpOnly

**Falta en diagrama:** Detalles de cómo se maneja el refresh (cookie vs body).

---

### 3. TABLA EVIDENCIA — EXISTE EN CÓDIGO PERO NO EN DIAGRAMA

**Código (Evidencia.java):**
```java
@Entity
@Table(name = "evidencia")
public class Evidencia {
    Long id                          // PK
    UUID publicId                    // UUIDv7 (dual-key)
    Long jrvId                       // FK (forward: acta será (jrv_id, cargo_id))
    Long cargoId                     // FK (forward: acta será (jrv_id, cargo_id))
    Long usuarioId                   // FK → usuario (quien subió)
    String s3Key                     // Path en S3: "actas/{jrvId}/{cargoId}/{uuid}.webp"
    long sizeBytes                   // Tamaño archivo
    String contentType               // MIME type (image/webp, etc)
    String sha256                    // Hex SHA-256 (64 chars)
    EstadoEvidencia estado           // EN_REVISION, APROBADA, RECHAZADA
    long version                     // @Version (optimistic locking)
    Instant creadoEn                 // Audit
    Instant actualizadoEn            // Audit
}
```

**Enum EstadoEvidencia:**
```java
EN_REVISION  // Recien subida, esperando validacion
APROBADA     // Validada, cuenta para resultado oficial
RECHAZADA    // Ilegible, mal encuadrada, rechazada
```

**En diagrama:**
- ❌ Tabla EVIDENCIA no mencionada
- ❓ Campo `storage_url` en ACTA podría referirse a esto, pero está mal modelado

**Impacto:** CRÍTICO. Evidencia es entidad IMPLEMENTADA con flujo completo (upload S3, cambio de estado). Diagrama no lo refleja.

---

### 4. TABLA ACTA — DOCUMENTADA PERO NO IMPLEMENTADA

**Diagrama define:**
```
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
```

**Código:** ❌ TABLA ACTA NO EXISTE EN CODIGO

**Pero:** Diagrama asume que:
- `storage_url` va en ACTA
- Estados (BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA) son string enum

**Realidad en código:**
- Evidencia.estado es ENUM (3 valores: EN_REVISION, APROBADA, RECHAZADA)
- Evidencia.s3Key es el equivalente a storage_url
- Estados de ACTA no están definidos en el código (porque ACTA no existe)

**Impacto:** PLANIFICACIÓN. Diagrama está correcto conceptualmente pero necesita:
- Creación de tabla ACTA + ActaRepository + ActaService
- Estados de ACTA como ENUM (6 estados no 3)
- Relación: ACTA (jrv_id, cargo_id, unique) + EVIDENCIA (s3_key, metadata)

---

### 5. IDENTIFICADORES — DUAL-KEY PATTERN (UUIDv7)

**Diagrama asume:**
```
Todas las tablas tienen:  uuid id PK
```

**Código implementa:**
```
Todas las tablas tienen:  
  Long id                 // PK autonumerico (BIGINT, rápido para FK)
  UUID publicId           // UUIDv7 (único, expuesto en API)
```

**Pattern:** Llamado "dual-key" o "UUIDv7 pattern"
- PK `id` se usa internamente (más rápido para FK)
- PublicId se expone en API/URL (no se ve el id autoincrement)

**Ejemplo:**
```java
GET /api/v1/evidencias/{publicId}  // Usuario ve UUIDv7
SELECT * FROM evidencia WHERE public_id = ?  // Query usa UUIDv7
SELECT * FROM usuario WHERE id = ?  // Join interno usa BIGINT
```

**En diagrama:** Necesita actualizar todas las PK a este patrón.

---

### 6. JUNCTION TABLES NO DOCUMENTADAS

**Código tiene:**

#### ROL_PERMISO (Junction)
```
rol_id FK → rol(id)
permiso_id FK → permiso(id)
```

**Mapeo:** Rol tiene N Permisos. Usuario → Rol → N Permisos.

**Ejemplo:** MASTER role tiene permisos ["usuario:create", "acta:aprobar", "acta:anular", ...]

**En diagrama:** Not shown.

---

#### MENU_PERMISO (Junction)
```
menu_id FK → menu(id)
permiso_id FK → permiso(id)
```

**Mapeo:** Menu es visible si usuario tiene ≥1 permiso del menu (OR logico).

**En diagrama:** Not shown.

---

### 7. AUDIT_LOG — PARCIALMENTE DOCUMENTADO

**Diagrama dice:**
```
AUDIT_LOG {
  uuid id PK
  uuid user_id FK
  uuid jrv_id FK
  uuid acta_id FK
  string accion
  jsonb payload
  timestamp timestamp
}
```

**Status:** ⏳ REFERENCIA. Existe en diagrama pero:
- Tabla no está en código aún (esperando que se cree con ActaService)
- Flujo: AuditLogService intercepta eventos → inserta en audit_log
- Campos OK pero necesita timestamp RENAME → created_at (convención)

---

### 8. NOTIFICACION — PARCIALMENTE DOCUMENTADO

**Diagrama dice:**
```
NOTIFICACION {
  uuid id PK
  uuid observer_id FK
  uuid acta_id FK
  string tipo
  boolean leida
  timestamp creada_en
}
```

**Status:** ⏳ PLANEADO. Tabla no existe en código.

**Falta en diagrama:**
- Cómo se genera (después de ACTA → EN_DISPUTA o APROBADA)
- Push notifications vs pull (polling vs WebSocket)
- Integración de WebSocket (mecanismo por definir)

---

## 🔧 Tabla de Correcciones Necesarias

| # | Tabla | Acción | Prioridad | Impacto |
|---|-------|--------|-----------|---------|
| 1 | USUARIO | Remover cedula/nombre_completo, agregar publicId/passwordHash/habilitado | CRÍTICA | Auth |
| 2 | ROL | Documentar (nueva entidad) | ALTA | RBAC |
| 3 | PERMISO | Documentar (nueva entidad) | ALTA | RBAC |
| 4 | MENU | Documentar (nueva entidad) | ALTA | UI sidebar |
| 5 | EVIDENCIA | Documentar (existe en código) | ALTA | Storage |
| 6 | ACTA | Corregir estado enum (6 valores, no 3) | ALTA | Actas |
| 7 | PK Pattern | Actualizar a dual-key (Long + UUID) | MEDIA | Consistency |
| 8 | Junctions | Documentar ROL_PERMISO, MENU_PERMISO | MEDIA | Clarity |

---

## 📊 Resumen

- **✅ Bien documentadas:** PROVINCIA, CANTON, PARROQUIA, RECINTO_ELECTORAL, JRV, JRV_CARGO, CANDIDATO, PARTIDO
- **⚠️ Necesita corrección:** USUARIO, ACTA, REFRESH_TOKEN
- **❌ No documentadas pero en código:** ROL, PERMISO, MENU, MENU_PERMISO, ROL_PERMISO, EVIDENCIA
- **⏳ No implementadas pero documentadas:** ACTA (solo estructura), AUDIT_LOG, NOTIFICACION

---

## 🎯 Recomendaciones

1. **Inmediato:** Corregir USUARIO diagram (cedula → publicId, rol string → FK)
2. **Inmediato:** Agregar ROL, PERMISO, MENU como nuevas entidades en diagrama
3. **Inmediato:** Documentar EVIDENCIA (ya existe en código)
4. **Antes de Fase 1 (Actas):** Actualizar ACTA states (6 valores) y relaciones con EVIDENCIA
5. **Convención:** Documenta dual-key pattern (Long PK + UUID publicId) en todas las tablas

