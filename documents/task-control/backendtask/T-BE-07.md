# T-BE-07 — ActaService: Disputa, Corrección y Aprobación

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-BE-06

---

## ¿Qué vas a construir?

Los métodos adicionales de `ActaService` que manejan el ciclo de vida del acta después de enviada:

```
ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA
                                  ↘ ANULADA (solo MASTER, solo desde APROBADA)
```

Cada transición tiene reglas estrictas: quién puede hacerla y desde qué estado.

---

## Conceptos clave

### Validación de estado antes de cada transición
Antes de cambiar el estado, siempre verifica que el estado actual permite esa transición:

```java
if (acta.getEstado() != EstadoActa.APROBADA) {
    throw new ActaEstadoInvalidoException("Solo se pueden anular actas APROBADAS");
}
```

### Notificaciones automáticas
Cada cambio de estado genera una notificación para el usuario correspondiente:
- `EN_DISPUTA` → notifica al OBSERVER dueño del acta
- `CORREGIDA` → notifica al COORDINATOR
- `ANULADA` → notifica a COORDINATOR

---

## Archivos a crear/modificar

```
src/main/java/ec/twintailcs/electoral/
  exception/
    ActaEstadoInvalidoException.java   ← nueva excepción
  service/
    ActaService.java                   ← agregar métodos
    NotificacionService.java           ← nuevo service de notificaciones
```

---

## Pasos

### Paso 1 — Nueva excepción

```java
// src/main/java/ec/twintailcs/electoral/exception/ActaEstadoInvalidoException.java
package ec.twintailcs.electoral.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ActaEstadoInvalidoException extends RuntimeException {
    public ActaEstadoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
```

### Paso 2 — NotificacionService

```java
// src/main/java/ec/twintailcs/electoral/service/NotificacionService.java
package ec.twintailcs.electoral.service;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.repository.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional
    public void notificar(Usuario destinatario, String tipo, Acta acta, String mensaje) {
        Notificacion notif = new Notificacion();
        notif.setDestinatario(destinatario);
        notif.setTipo(tipo);
        notif.setActa(acta);
        notif.setMensaje(mensaje);
        notif.setLeida(false);
        notificacionRepository.save(notif);
    }
}
```

### Paso 3 — Agregar métodos a ActaService

Agrega estos métodos dentro de la clase `ActaService` que ya tienes de T-BE-06:

```java
// ─── Inyectar NotificacionService en el constructor ───────────────────────────
// Agrega al constructor existente:
private final NotificacionService notificacionService;

// ─── marcarEnDisputa ──────────────────────────────────────────────────────────

/**
 * COORDINATOR o MASTER marca un acta como EN_DISPUTA.
 * Notifica al OBSERVER dueño del acta.
 */
@Transactional
public Acta marcarEnDisputa(Long actaId, Long coordinatorId, String motivo, HttpServletRequest request) {

    Acta acta = actaRepository.findById(actaId)
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    // Solo se puede marcar en disputa si está ENVIADA o CORREGIDA
    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
        throw new ActaEstadoInvalidoException(
            "No se puede marcar en disputa. Estado actual: " + acta.getEstado()
        );
    }

    acta.setEstado(EstadoActa.EN_DISPUTA);
    Acta actaActualizada = actaRepository.save(acta);

    // Notificar al observer dueño del acta
    notificacionService.notificar(
        acta.getObserver(),
        "ACTA_EN_DISPUTA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue marcada en disputa. Motivo: " + motivo
    );

    auditLogService.registrar(coordinatorId, "ACTA_EN_DISPUTA", "acta", actaId,
        "Motivo: " + motivo, request);

    return actaActualizada;
}

// ─── corregir ─────────────────────────────────────────────────────────────────

/**
 * OBSERVER corrige su propia acta EN_DISPUTA.
 * COORDINATOR puede corregir cualquier acta EN_DISPUTA directamente.
 */
@Transactional
public Acta corregir(Long actaId, ActaRequestDto dto, Long usuarioId,
                     boolean esCoordinator, HttpServletRequest request) {

    Acta acta = actaRepository.findById(actaId)
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    // Solo se puede corregir si está EN_DISPUTA
    if (acta.getEstado() != EstadoActa.EN_DISPUTA) {
        throw new ActaEstadoInvalidoException(
            "Solo se pueden corregir actas EN_DISPUTA. Estado actual: " + acta.getEstado()
        );
    }

    // Si es OBSERVER, verificar que es el dueño del acta
    if (!esCoordinator && !acta.getObserver().getId().equals(usuarioId)) {
        throw new AccesoNoPermitidoException("Solo puedes corregir tus propias actas");
    }

    // Validar que los nuevos votos cuadran
    int sumaVotos = dto.getDetalles().stream().mapToInt(ActaDetalleDto::getVotos).sum();
    int total = sumaVotos + dto.getVotosBlancos() + dto.getVotosNulos();

    if (total != dto.getTotalSufragantes()) {
        throw new ActaInvalidaException("Los votos no cuadran: " + total + " ≠ " + dto.getTotalSufragantes());
    }

    // Actualizar datos del acta
    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());
    acta.setEstado(EstadoActa.CORREGIDA);

    // Borrar detalles anteriores y reemplazar con los nuevos
    actaDetalleRepository.deleteByActa(acta);
    for (ActaDetalleDto detalleDto : dto.getDetalles()) {
        Candidato candidato = candidatoRepository.findById(detalleDto.getCandidatoId())
            .orElseThrow(() -> new ActaInvalidaException("Candidato no encontrado: " + detalleDto.getCandidatoId()));

        ActaDetalle detalle = new ActaDetalle();
        detalle.setActa(acta);
        detalle.setCandidato(candidato);
        detalle.setVotos(detalleDto.getVotos());
        actaDetalleRepository.save(detalle);
    }

    Acta actaActualizada = actaRepository.save(acta);

    // Notificar al coordinator que fue corregida
    // (En un sistema real, buscarías los coordinators activos)
    auditLogService.registrar(usuarioId, "ACTA_CORREGIDA", "acta", actaId,
        "Acta corregida por usuario " + usuarioId, request);

    return actaActualizada;
}

// ─── aprobar ──────────────────────────────────────────────────────────────────

/**
 * COORDINATOR o MASTER aprueba un acta ENVIADA o CORREGIDA.
 */
@Transactional
public Acta aprobar(Long actaId, Long coordinatorId, HttpServletRequest request) {

    Acta acta = actaRepository.findById(actaId)
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    // Se puede aprobar desde ENVIADA o CORREGIDA
    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
        throw new ActaEstadoInvalidoException(
            "Solo se pueden aprobar actas ENVIADAS o CORREGIDAS. Estado actual: " + acta.getEstado()
        );
    }

    acta.setEstado(EstadoActa.APROBADA);
    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrar(coordinatorId, "ACTA_APROBADA", "acta", actaId,
        "Acta aprobada por coordinator " + coordinatorId, request);

    return actaActualizada;
}

// ─── anular ───────────────────────────────────────────────────────────────────

/**
 * Solo MASTER puede anular un acta.
 * Solo se pueden anular actas en estado APROBADA.
 * Los datos NO se borran — solo cambia el estado.
 */
@Transactional
public Acta anular(Long actaId, Long masterId, String motivo, HttpServletRequest request) {

    Acta acta = actaRepository.findById(actaId)
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));

    // SOLO actas APROBADAS se pueden anular
    if (acta.getEstado() != EstadoActa.APROBADA) {
        throw new ActaEstadoInvalidoException(
            "Solo se pueden anular actas APROBADAS. Estado actual: " + acta.getEstado()
        );
    }

    acta.setEstado(EstadoActa.ANULADA);
    acta.setMotivoAnulacion(motivo);  // registrar el motivo
    // Los datos (votos, detalles) NO se modifican — solo el estado cambia
    Acta actaActualizada = actaRepository.save(acta);

    // Notificar al observer y al coordinator
    notificacionService.notificar(
        acta.getObserver(),
        "ACTA_ANULADA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue anulada. Motivo: " + motivo
    );

    auditLogService.registrar(masterId, "ACTA_ANULADA", "acta", actaId,
        "Motivo: " + motivo, request);

    return actaActualizada;
}
```

---

## Cómo verificar que funciona

Prueba el flujo completo cuando tengas los controllers:

1. POST `/api/actas` → Estado: ENVIADA
2. PUT `/api/actas/1/disputar` → Estado: EN_DISPUTA
3. PUT `/api/actas/1/corregir` → Estado: CORREGIDA
4. PUT `/api/actas/1/aprobar` → Estado: APROBADA

Verifica en BD:
```sql
SELECT id, estado, motivo_anulacion FROM acta;
SELECT * FROM notificacion ORDER BY creada_en DESC;
SELECT * FROM audit_log WHERE entidad = 'acta' ORDER BY timestamp DESC;
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| Puedo anular acta ENVIADA | Falta la validación de estado | Verifica el `if` antes de cambiar el estado |
| Observer puede corregir acta de otro | Falta la verificación de dueño | Verifica `acta.getObserver().getId().equals(usuarioId)` |
| No se crean notificaciones | `NotificacionService` no inyectado | Agrega al constructor de `ActaService` |
| `deleteByActa` no borra | Falta `@Modifying` + `@Transactional` en el repo | Revisa `ActaDetalleRepository` |
