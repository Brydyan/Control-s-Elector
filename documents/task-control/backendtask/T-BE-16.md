# T-BE-16 — Clasificación de Observaciones en Evidencia

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-BE-14 (StorageService + upload básico ya existe)
**Independencia:** ✅ Esta tarea NO depende de T-BE-03 (Acta), por eso se puede implementar ya

---

## ¿Qué vas a construir?

Extensión del flujo de subida de evidencias: después de que un OBSERVER sube la foto del acta, un botón le pregunta si hay observaciones (problemas/discrepancias). Si responde "Sí", se despliega un selector para que marque 1-2 categorías de observación (de un set predefinido **TBD**), para ayudar a COORDINATOR/ADMIN a priorizar la revisión.

**Por qué:** Acelera la clasificación de casos urgentes sin esperar a que COORDINATOR revise todo. Una foto borrosa es diferente de "datos no coinciden" — ambas requieren atención pero distinto tipo de revisión.

**Nota importante:** Esta tarea extiende `Evidencia` (que ya está implementada y funciona en producción). NO depende de que Acta/Notificacion estén listos. Se puede hacer ahora.

---

## Conceptos clave

### Enum vs. tabla separada para categorías
Dos opciones:
- **Enum `TipoObservacion`** (recomendado si las categorías son fijas): crea `@Enumerated(EnumType.STRING)` en `Evidencia` o una tabla junction `evidencia_observacion` que relacione `evidencia_id` + `tipo_observacion_id`
- **Tabla separada `TipoObservacion`** (si las categorías evolucionan): permite agregar/quitar categorías sin tocar código

Para esta versión, asumimos categorías fijas → **enum**.

### Validación de máximo 2 observaciones
En la API: `@Constraint` annotation o validation en el servicio. Máximo 2 categorías.

### RLS en observaciones
Las observaciones de una evidencia son visibles para:
- El OBSERVER dueño (quien la subió)
- COORDINATOR, MASTER, ADMIN (lectura)
Solo el OBSERVER dueño puede ESCRIBIR observaciones.

---

## Archivos a crear/modificar

```
src/main/java/ec/twintailcs/electoral/evidencia/domain/
  TipoObservacion.java      ← Enum (nuevo)
  EvidenciaObservacion.java ← Entidad junction (si vas a tabla, opcional)

src/main/java/ec/twintailcs/electoral/evidencia/
  repository/
    EvidenciaObservacionRepository.java  (si usas tabla)
  
  web/dto/
    EvidenciaConObservacionesDto.java  ← DTO de respuesta

  web/
    EvidenciaController.java           ← agregar PATCH endpoint

src/main/java/ec/twintailcs/electoral/evidencia/application/
  EvidenciaService.java                ← agregar método marcarObservaciones()
```

---

## Pasos

### Paso 1 — Enum TipoObservacion

**⚠️ CATEGORÍAS TBD:** El usuario definirá el set específico antes de implementar. Por ahora, valores de placeholder basados en los motivos de disputa que aparecen en los mockups COORDINATOR:

```java
// src/main/java/ec/twintailcs/electoral/evidencia/domain/TipoObservacion.java
package ec.twintailcs.electoral.evidencia.domain;

/**
 * Categorías de observación sobre una evidencia (foto del acta).
 * Seleccionables por OBSERVER al finalizar la subida (máximo 2).
 * 
 * VALORES DEFINITIVOS: TBD — pendiente definición del usuario.
 * Placeholders actuales basados en mockups COORDINATOR:
 */
public enum TipoObservacion {
    FOTO_BORROSA,              // "Imagen fuera de foco" / foto deficiente
    DATOS_NO_COINCIDEN,        // "Votos no coinciden con total sufragantes"
    FIRMA_FALTANTE,            // "Firma del veedor faltante"
    NUMERO_INCORRECTO,         // "Número de mesa incorrecto"
    OTRA                       // Categoría abierta para casos no clasificados
}
```

### Paso 2 — Extender Evidencia (opción A: campo JSON simple)

Si las observaciones son solo una lista de enums sin metadatos adicionales, usa un campo JSON:

```java
// En Evidencia.java — agregar:
@Column(columnDefinition = "jsonb", nullable = true)
private List<TipoObservacion> observaciones = new ArrayList<>();  // máx 2

// Getters/Setters
public List<TipoObservacion> getObservaciones() { return observaciones; }
public void setObservaciones(List<TipoObservacion> obs) {
    // Validar máximo 2
    if (obs != null && obs.size() > 2) {
        throw new IllegalArgumentException("Máximo 2 observaciones permitidas");
    }
    this.observaciones = obs;
}
```

**Alternativa B:** Si necesitas metadatos por observación (ej: cuándo se añadió, quién, descripción), crea tabla `evidencia_observacion` con FK a `evidencia_id` + `tipo` enum.

### Paso 3 — DTO de respuesta

```java
// src/main/java/ec/twintailcs/electoral/evidencia/web/dto/EvidenciaConObservacionesDto.java
package ec.twintailcs.electoral.evidencia.web.dto;

import java.util.List;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;

public class EvidenciaConObservacionesDto {
    private Long id;
    private String s3Key;
    private String estado;
    private List<TipoObservacion> observaciones;

    public EvidenciaConObservacionesDto(Long id, String s3Key, String estado, List<TipoObservacion> observaciones) {
        this.id = id;
        this.s3Key = s3Key;
        this.estado = estado;
        this.observaciones = observaciones;
    }

    // Getters
    public Long getId() { return id; }
    public String getS3Key() { return s3Key; }
    public String getEstado() { return estado; }
    public List<TipoObservacion> getObservaciones() { return observaciones; }
}
```

### Paso 4 — Endpoint PATCH en EvidenciaController

```java
// En EvidenciaController.java — agregar:

import java.util.List;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaConObservacionesDto;

/**
 * PATCH /api/evidencias/{id}/observaciones
 * Solo el OBSERVER dueño (quien subió la evidencia) puede marcar observaciones.
 * Máximo 2 categorías.
 */
@PatchMapping("/{id}/observaciones")
@PreAuthorize("hasRole('OBSERVER')")
public ResponseEntity<EvidenciaConObservacionesDto> marcarObservaciones(
        @PathVariable Long id,
        @RequestBody MarcarObservacionesRequest request) {

    Long observerId = getObserverId();  // Desde SecurityContextHolder
    Evidencia evidencia = evidenciaRepository.findById(id)
        .orElseThrow(() -> new EvidenciaNotFoundException(id));

    // Validar que el OBSERVER es el dueño
    if (!evidencia.getUsuarioId().equals(observerId)) {
        throw new AccessDeniedException("No tienes permiso para marcar observaciones en esta evidencia");
    }

    // Marcar observaciones (servicio valida máximo 2)
    evidenciaService.marcarObservaciones(id, request.getObservaciones());

    // Retornar evidencia con observaciones
    Evidencia updated = evidenciaRepository.findById(id).orElseThrow();
    return ResponseEntity.ok(new EvidenciaConObservacionesDto(
        updated.getId(),
        updated.getS3Key(),
        updated.getEstado().name(),
        updated.getObservaciones()
    ));
}
```

DTO del request:

```java
// src/main/java/ec/twintailcs/electoral/evidencia/web/dto/MarcarObservacionesRequest.java
package ec.twintailcs.electoral.evidencia.web.dto;

import java.util.List;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;

public class MarcarObservacionesRequest {
    private List<TipoObservacion> observaciones;

    public MarcarObservacionesRequest() {}
    public MarcarObservacionesRequest(List<TipoObservacion> observaciones) {
        this.observaciones = observaciones;
    }

    public List<TipoObservacion> getObservaciones() { return observaciones; }
    public void setObservaciones(List<TipoObservacion> observaciones) { this.observaciones = observaciones; }
}
```

### Paso 5 — Método en EvidenciaService

```java
// En EvidenciaService.java — agregar:

/**
 * Marca observaciones en una evidencia.
 * Valida máximo 2 categorías.
 */
public void marcarObservaciones(Long evidenciaId, List<TipoObservacion> observaciones) {
    if (observaciones == null || observaciones.isEmpty()) {
        observaciones = new ArrayList<>();
    }
    
    if (observaciones.size() > 2) {
        throw new IllegalArgumentException("Máximo 2 observaciones permitidas, recibidas: " + observaciones.size());
    }

    Evidencia evidencia = repository.findById(evidenciaId)
        .orElseThrow(() -> new EvidenciaNotFoundException(evidenciaId));
    
    evidencia.setObservaciones(observaciones);
    repository.save(evidencia);
    
    // Opcional: auditar
    auditLogService.registrar(
        getCurrentUserId(),
        "EVIDENCIA_OBSERVACIONES_MARCADAS",
        "evidencia",
        evidenciaId,
        "Observaciones: " + observaciones.toString()
    );
}
```

---

## Integración con flujo frontend OBSERVER

En `documents/mockups/OBSERVER/06-revision-envio.html`, después del botón "📤 Enviar Acta", agregar un paso:

```
┌─────────────────────────────────┐
│ ¿Hay observaciones en la evidencia?  │
│ [NO] [SÍ]                           │
└─────────────────────────────────┘
  SI → despliega checkboxes (max 2):
      ☐ Foto borrosa
      ☐ Datos no coinciden
      ☐ Firma faltante
      ☐ Número incorrecto
      ☐ Otra
```

El frontend (Angular) hace:
```typescript
// Después de POST /api/observer/actas/{actaId}/imagen (subida OK)
// Si usuario marca observaciones, hace:
PATCH /api/evidencias/{evidenciaId}/observaciones
Body: { "observaciones": ["FOTO_BORROSA", "DATOS_NO_COINCIDEN"] }
```

---

## Visibilidad en dashboard ADMIN

El endpoint `GET /api/dashboard/novedades` (de T-BE-13) incluye evidencias que tengan observaciones marcadas:

```
GET /api/dashboard/novedades
→ [
    { tipo: "ACTA_EN_DISPUTA", ... },
    { tipo: "EVIDENCIA_CON_OBS", observaciones: ["FOTO_BORROSA"], ... }
  ]
```

---

## Cómo verificar que funciona

1. **Arrange:** Sube una evidencia vía `POST /api/evidencias` (ya funciona desde T-BE-14).

2. **Act:** Marca observaciones:
```bash
curl -X PATCH http://localhost:8081/api/evidencias/1/observaciones \
  -H "Authorization: Bearer TOKEN_OBSERVER" \
  -H "Content-Type: application/json" \
  -d '{"observaciones": ["FOTO_BORROSA", "DATOS_NO_COINCIDEN"]}'
```

3. **Assert:**
- Respuesta: 200 OK + DTO con observaciones
- Query BD: `SELECT observaciones FROM evidencia WHERE id=1;` → `["FOTO_BORROSA", "DATOS_NO_COINCIDEN"]`
- Dashboard ADMIN: `GET /api/dashboard/novedades` incluye la evidencia

4. **Validación de máximo 2:**
```bash
curl -X PATCH http://localhost:8081/api/evidencias/1/observaciones \
  -d '{"observaciones": ["FOTO_BORROSA", "DATOS_NO_COINCIDEN", "FIRMA_FALTANTE"]}'
# → 400 Bad Request: "Máximo 2 observaciones permitidas, recibidas: 3"
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `403` Forbidden | OBSERVER intenta marcar obs de otra evidencia | Valida `usuario_id` en el servicio |
| `@Column jsonb` falla | Base de datos no es PostgreSQL | Usa tipo `@ElementCollection` de JPA (portátil) en vez de `jsonb` |
| Observaciones aparecen NULL | No inicializaste `new ArrayList<>()` | Agrega `= new ArrayList<>()` en el campo |
| Máximo 2 no se valida | Falta validación en setObservaciones() | Agrega `if (obs.size() > 2) throw ...` |
| OBSERVER ve obs de otro | Falta RLS en evidencia | Verifica RLS: `WHERE usuario_id = current_user_id()` (T-BE-03) |

---

## Notas

- **Categorías TBD:** El usuario debe definir los valores finales de `TipoObservacion` enum. Los actuales son placeholders pedagógicos.
- **Independencia:** Esta tarea se puede implementar AHORA, sin esperar a que Acta/Notificacion estén listos.
- **RLS:** Depende de que T-IN-03 (RLS en BD) y T-BE-03 (audit del lado usuario) estén listos. Para testing en dev sin RLS, simplemente valida en la API.
