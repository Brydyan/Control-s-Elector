# T-BE-12 — Endpoints de Coordinación (COORDINATOR + MASTER)

**Dificultad:** Intermedio
**Tiempo estimado:** 2 horas
**Depende de:** T-BE-07, T-BE-09

---

## ¿Qué vas a construir?

Los endpoints globales para COORDINATOR y MASTER:
- Listar todas las actas con filtros
- Marcar en disputa
- Corregir directamente
- Aprobar
- Anular (solo MASTER)

---

## Conceptos clave

### @RequestParam opcional
Un parámetro de query que no es obligatorio:
```java
// GET /api/actas?estado=ENVIADA&cargoId=3
@GetMapping
public List<Acta> listar(
    @RequestParam(required = false) EstadoActa estado,
    @RequestParam(required = false) Long cargoId
) { }
```
Si el cliente no envía el parámetro, Spring lo recibe como `null`.

### @PreAuthorize con múltiples roles
```java
@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER')")
```

### Diferencia COORDINATOR vs MASTER en anulación
Solo MASTER puede anular. En el controller verificamos esto con `@PreAuthorize`:
```java
@DeleteMapping("/{actaId}")
@PreAuthorize("hasRole('MASTER')")  // Restringe a MASTER solo este endpoint
public void anular(...) { }
```

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/
  dto/
    DisputaDto.java
    AnulacionDto.java
  controller/
    CoordinatorController.java
```

---

## Pasos

### Paso 1 — DTOs para acciones con motivo

```java
// src/main/java/ec/twintailcs/electoral/dto/DisputaDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.NotBlank;

public class DisputaDto {
    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/AnulacionDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.NotBlank;

public class AnulacionDto {
    @NotBlank(message = "El motivo de anulación es obligatorio")
    private String motivo;

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
```

### Paso 2 — CoordinatorController

```java
// src/main/java/ec/twintailcs/electoral/controller/CoordinatorController.java
package ec.twintailcs.electoral.controller;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.repository.ActaRepository;
import ec.twintailcs.electoral.service.ActaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coordinator")
@PreAuthorize("hasAnyRole('COORDINATOR', 'MASTER')")  // Ambos roles pueden acceder
public class CoordinatorController {

    private final ActaService actaService;
    private final ActaRepository actaRepository;

    public CoordinatorController(ActaService actaService, ActaRepository actaRepository) {
        this.actaService = actaService;
        this.actaRepository = actaRepository;
    }

    private Long getUsuarioId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * GET /api/coordinator/actas
     * Lista actas con filtros opcionales.
     * Ejemplo: GET /api/coordinator/actas?estado=ENVIADA&jrvId=5
     */
    @GetMapping("/actas")
    public ResponseEntity<List<Acta>> listarActas(
            @RequestParam(required = false) EstadoActa estado,
            @RequestParam(required = false) Long jrvId,
            @RequestParam(required = false) Long cargoId) {

        // Si todos los filtros son null, retorna todas las actas
        List<Acta> actas = actaRepository.findWithFilters(estado, jrvId, cargoId);
        return ResponseEntity.ok(actas);
    }

    /**
     * GET /api/coordinator/actas/{actaId}
     * Ver detalle de un acta específica.
     */
    @GetMapping("/actas/{actaId}")
    public ResponseEntity<Acta> verActa(@PathVariable Long actaId) {
        Acta acta = actaRepository.findById(actaId)
            .orElseThrow(() -> new RuntimeException("Acta no encontrada: " + actaId));
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/coordinator/actas/{actaId}/disputar
     * Marcar un acta como EN_DISPUTA, con motivo obligatorio.
     */
    @PutMapping("/actas/{actaId}/disputar")
    public ResponseEntity<Acta> disputar(@PathVariable Long actaId,
                                          @Valid @RequestBody DisputaDto dto,
                                          HttpServletRequest request) {
        Acta acta = actaService.marcarEnDisputa(actaId, getUsuarioId(), dto.getMotivo(), request);
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/coordinator/actas/{actaId}/corregir
     * COORDINATOR corrige directamente el acta EN_DISPUTA.
     */
    @PutMapping("/actas/{actaId}/corregir")
    public ResponseEntity<Acta> corregir(@PathVariable Long actaId,
                                          @Valid @RequestBody ActaRequestDto dto,
                                          HttpServletRequest request) {
        // true = esCoordinator → puede corregir cualquier acta, no solo las propias
        Acta acta = actaService.corregir(actaId, dto, getUsuarioId(), true, request);
        return ResponseEntity.ok(acta);
    }

    /**
     * PUT /api/coordinator/actas/{actaId}/aprobar
     * Aprobar un acta ENVIADA o CORREGIDA.
     */
    @PutMapping("/actas/{actaId}/aprobar")
    public ResponseEntity<Acta> aprobar(@PathVariable Long actaId,
                                         HttpServletRequest request) {
        Acta acta = actaService.aprobar(actaId, getUsuarioId(), request);
        return ResponseEntity.ok(acta);
    }

    /**
     * DELETE /api/coordinator/actas/{actaId}
     * Anular un acta APROBADA. SOLO MASTER puede hacer esto.
     * Aunque la clase permite COORDINATOR y MASTER, este método restringe solo a MASTER.
     */
    @DeleteMapping("/actas/{actaId}")
    @PreAuthorize("hasRole('MASTER')")  // Override del @PreAuthorize de clase
    public ResponseEntity<Acta> anular(@PathVariable Long actaId,
                                        @Valid @RequestBody AnulacionDto dto,
                                        HttpServletRequest request) {
        Acta acta = actaService.anular(actaId, getUsuarioId(), dto.getMotivo(), request);
        return ResponseEntity.ok(acta);
    }
}
```

---

## Cómo verificar que funciona

```bash
# Listar actas enviadas
curl "http://localhost:8080/api/coordinator/actas?estado=ENVIADA" \
  -H "Authorization: Bearer TOKEN_COORDINATOR"

# Marcar en disputa
curl -X PUT http://localhost:8080/api/coordinator/actas/1/disputar \
  -H "Authorization: Bearer TOKEN_COORDINATOR" \
  -H "Content-Type: application/json" \
  -d '{"motivo": "Los votos del candidato 2 no coinciden con el acta física"}'

# Aprobar
curl -X PUT http://localhost:8080/api/coordinator/actas/1/aprobar \
  -H "Authorization: Bearer TOKEN_COORDINATOR"

# Anular (solo MASTER)
curl -X DELETE http://localhost:8080/api/coordinator/actas/1 \
  -H "Authorization: Bearer TOKEN_MASTER" \
  -H "Content-Type: application/json" \
  -d '{"motivo": "Error en el recuento oficial"}'

# Coordinator intentando anular — debe dar 403
curl -X DELETE http://localhost:8080/api/coordinator/actas/1 \
  -H "Authorization: Bearer TOKEN_COORDINATOR" \
  -H "Content-Type: application/json" \
  -d '{"motivo": "Test"}'
# → 403 Forbidden
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `409` al disputar | Acta no está en estado correcto | Solo ENVIADA o CORREGIDA se pueden disputar |
| `403` al anular con COORDINATOR | Correcto — solo MASTER puede | Usa token de MASTER |
| Lista vacía en GET `/actas` | No hay actas en BD | Primero registra actas con el observer |
| Filtro `estado` no funciona | Pasas texto en URL | Asegúrate que el texto coincide exactamente: `ENVIADA`, `EN_DISPUTA`, etc. |
