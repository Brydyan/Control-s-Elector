# T-BE-06 — ActaService: Registro con Locking Pesimista

**Dificultad:** Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-BE-05

---

## ¿Qué vas a construir?

El servicio más crítico del sistema: el que registra actas. Tiene dos responsabilidades principales:

1. **Validar** que los votos suman correctamente
2. **Evitar duplicados** con locking pesimista (si dos observers intentan registrar la misma JRV+cargo al mismo tiempo, solo uno gana)

---

## Conceptos clave

### @Transactional
Envuelve el método en una transacción de base de datos. Si algo falla en el medio, todo se revierte (rollback). Absolutamente necesario para el locking pesimista.

```java
@Transactional  // ← sin esto, el lock pesimista NO funciona
public Acta registrar(ActaRequestDto dto, Long observerId) {
    // ...
}
```

### Locking Pesimista — ¿por qué?
Imagina que dos observers A y B intentan registrar la JRV 42 + cargo Alcalde al mismo tiempo:

```
Observer A: busca → no existe acta → va a insertar...
Observer B: busca → no existe acta → va a insertar...  ← problema!
Observer A: inserta → OK
Observer B: inserta → ¡duplicado!
```

Con locking pesimista (`SELECT FOR UPDATE`), el segundo observer espera hasta que el primero termina. Así:

```
Observer A: busca con LOCK → no existe → inserta → commit → libera lock
Observer B: busca con LOCK → espera... → existe → lanza error → NO inserta
```

### Validación de acta
La regla de negocio más importante:
```
SUM(votos por candidato) + votos_nulos + votos_blancos = total_sufragantes
```
Si esta suma no cuadra, el acta es inválida.

### DTO (Data Transfer Object)
Un objeto simple (sin anotaciones JPA) que representa los datos que llegan del formulario del frontend. Nunca expones las entidades directamente.

---

## Archivos a crear/modificar

```
src/main/java/ec/twintailcs/electoral/
  dto/
    ActaRequestDto.java        ← datos del formulario
    ActaDetalleDto.java        ← votos por candidato
  exception/
    ActaInvalidaException.java
    JrvCargoYaRegistradoException.java
    AccesoNoPermitidoException.java
  service/
    ActaService.java
```

---

## Pasos

### Paso 1 — DTOs

```java
// src/main/java/ec/twintailcs/electoral/dto/ActaDetalleDto.java
package ec.twintailcs.electoral.dto;

public class ActaDetalleDto {
    private Long candidatoId;
    private Integer votos;

    // Getters y Setters
    public Long getCandidatoId() { return candidatoId; }
    public void setCandidatoId(Long candidatoId) { this.candidatoId = candidatoId; }
    public Integer getVotos() { return votos; }
    public void setVotos(Integer votos) { this.votos = votos; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/ActaRequestDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public class ActaRequestDto {

    @NotNull(message = "jrvId es obligatorio")
    private Long jrvId;

    @NotNull(message = "cargoId es obligatorio")
    private Long cargoId;

    @NotNull
    @Min(value = 0, message = "totalSufragantes no puede ser negativo")
    private Integer totalSufragantes;

    @NotNull
    @Min(0)
    private Integer votosBlancos;

    @NotNull
    @Min(0)
    private Integer votosNulos;

    @NotEmpty(message = "Debe haber al menos un candidato")
    @Valid
    private List<ActaDetalleDto> detalles;

    // Getters y Setters para todos los campos
    public Long getJrvId() { return jrvId; }
    public void setJrvId(Long jrvId) { this.jrvId = jrvId; }
    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Integer getTotalSufragantes() { return totalSufragantes; }
    public void setTotalSufragantes(Integer t) { this.totalSufragantes = t; }
    public Integer getVotosBlancos() { return votosBlancos; }
    public void setVotosBlancos(Integer v) { this.votosBlancos = v; }
    public Integer getVotosNulos() { return votosNulos; }
    public void setVotosNulos(Integer v) { this.votosNulos = v; }
    public List<ActaDetalleDto> getDetalles() { return detalles; }
    public void setDetalles(List<ActaDetalleDto> detalles) { this.detalles = detalles; }
}
```

### Paso 2 — Excepciones custom

```java
// src/main/java/ec/twintailcs/electoral/exception/ActaInvalidaException.java
package ec.twintailcs.electoral.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// @ResponseStatus hace que Spring devuelva automáticamente HTTP 400 cuando lanzamos esta excepción
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ActaInvalidaException extends RuntimeException {
    public ActaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
```

```java
// src/main/java/ec/twintailcs/electoral/exception/JrvCargoYaRegistradoException.java
package ec.twintailcs.electoral.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)  // HTTP 409 Conflict
public class JrvCargoYaRegistradoException extends RuntimeException {
    public JrvCargoYaRegistradoException(Long jrvId, Long cargoId) {
        super("Ya existe un acta activa para JRV " + jrvId + " y cargo " + cargoId);
    }
}
```

```java
// src/main/java/ec/twintailcs/electoral/exception/AccesoNoPermitidoException.java
package ec.twintailcs.electoral.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)  // HTTP 403 Forbidden
public class AccesoNoPermitidoException extends RuntimeException {
    public AccesoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
```

### Paso 3 — ActaService

```java
// src/main/java/ec/twintailcs/electoral/service/ActaService.java
package ec.twintailcs.electoral.service;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.exception.*;
import ec.twintailcs.electoral.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActaService {

    private final ActaRepository actaRepository;
    private final ActaDetalleRepository actaDetalleRepository;
    private final JrvRepository jrvRepository;
    private final UsuarioRepository usuarioRepository;
    private final CandidatoRepository candidatoRepository;
    private final AuditLogService auditLogService;

    // Constructor — Spring inyecta todo automáticamente
    public ActaService(ActaRepository actaRepository,
                       ActaDetalleRepository actaDetalleRepository,
                       JrvRepository jrvRepository,
                       UsuarioRepository usuarioRepository,
                       CandidatoRepository candidatoRepository,
                       AuditLogService auditLogService) {
        this.actaRepository = actaRepository;
        this.actaDetalleRepository = actaDetalleRepository;
        this.jrvRepository = jrvRepository;
        this.usuarioRepository = usuarioRepository;
        this.candidatoRepository = candidatoRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Registra una nueva acta.
     * Solo puede llamarlo un OBSERVER autenticado.
     *
     * Flujo:
     * 1. Verificar que el observer tiene asignación en esa JRV
     * 2. Bloquear con lock pesimista para evitar duplicados
     * 3. Validar que los votos suman correctamente
     * 4. Guardar acta + detalles
     * 5. Registrar en AuditLog
     */
    @Transactional  // ← OBLIGATORIO para que el lock pesimista funcione
    public Acta registrar(ActaRequestDto dto, Long observerId, HttpServletRequest request) {

        // 1. Cargar entidades desde la BD
        Jrv jrv = jrvRepository.findById(dto.getJrvId())
            .orElseThrow(() -> new ActaInvalidaException("JRV no encontrada: " + dto.getJrvId()));

        Cargo cargo = cargoRepository.findById(dto.getCargoId())
            .orElseThrow(() -> new ActaInvalidaException("Cargo no encontrado: " + dto.getCargoId()));

        Usuario observer = usuarioRepository.findById(observerId)
            .orElseThrow(() -> new ActaInvalidaException("Observer no encontrado"));

        // 2. Verificar que el observer tiene asignación en esa JRV
        boolean tieneAsignacion = asignacionRepository.existsByObserverAndJrv(observer, jrv);
        if (!tieneAsignacion) {
            throw new AccesoNoPermitidoException(
                "Observer " + observerId + " no tiene asignación en JRV " + dto.getJrvId()
            );
        }

        // 3. Lock pesimista — busca acta existente bloqueando la fila
        // Si dos requests llegan al mismo tiempo, el segundo espera aquí
        actaRepository.findActaActivaConLock(jrv, cargo).ifPresent(actaExistente -> {
            throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
        });

        // 4. Validar que los votos cuadran
        int sumaVotos = dto.getDetalles().stream()
            .mapToInt(ActaDetalleDto::getVotos)
            .sum();

        int total = sumaVotos + dto.getVotosBlancos() + dto.getVotosNulos();

        if (total != dto.getTotalSufragantes()) {
            throw new ActaInvalidaException(
                "Los votos no cuadran. " +
                "Candidatos: " + sumaVotos +
                " + Blancos: " + dto.getVotosBlancos() +
                " + Nulos: " + dto.getVotosNulos() +
                " = " + total +
                " ≠ " + dto.getTotalSufragantes() + " (total sufragantes)"
            );
        }

        // 5. Crear y guardar el acta
        Acta acta = new Acta();
        acta.setJrv(jrv);
        acta.setCargo(cargo);
        acta.setObserver(observer);
        acta.setEstado(EstadoActa.ENVIADA); // directo a ENVIADA al registrar
        acta.setTotalSufragantes(dto.getTotalSufragantes());
        acta.setVotosBlancos(dto.getVotosBlancos());
        acta.setVotosNulos(dto.getVotosNulos());

        Acta actaGuardada = actaRepository.save(acta);

        // 6. Guardar los detalles (votos por candidato)
        for (ActaDetalleDto detalleDto : dto.getDetalles()) {
            Candidato candidato = candidatoRepository.findById(detalleDto.getCandidatoId())
                .orElseThrow(() -> new ActaInvalidaException(
                    "Candidato no encontrado: " + detalleDto.getCandidatoId()
                ));

            ActaDetalle detalle = new ActaDetalle();
            detalle.setActa(actaGuardada);
            detalle.setCandidato(candidato);
            detalle.setVotos(detalleDto.getVotos());
            actaDetalleRepository.save(detalle);
        }

        // 7. Registrar en AuditLog — SIEMPRE al final
        auditLogService.registrar(
            observerId,
            "REGISTRO_ACTA",
            "acta",
            actaGuardada.getId(),
            "Acta registrada: JRV=" + dto.getJrvId() + ", cargo=" + dto.getCargoId() +
            ", sufragantes=" + dto.getTotalSufragantes(),
            request
        );

        return actaGuardada;
    }
}
```

---

## Cómo verificar que funciona

Cuando tengas el controller (T-BE-11), prueba con curl o Postman:

```json
POST /api/actas
{
  "jrvId": 1,
  "cargoId": 1,
  "totalSufragantes": 100,
  "votosBlancos": 5,
  "votosNulos": 3,
  "detalles": [
    { "candidatoId": 1, "votos": 60 },
    { "candidatoId": 2, "votos": 32 }
  ]
}
```
Verifica: 60 + 32 + 5 + 3 = 100 ✓

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| Lock no previene duplicados | Falta `@Transactional` | Agrégalo al método `registrar()` |
| `LazyInitializationException` | Accedes a relación fuera de la transacción | Usa `@Transactional` o carga la relación en el mismo método |
| `could not serialize access` | Dos transacciones chocan | Normal con lock pesimista — el segundo debe recibir el error |
| Votos negativos aceptados | Falta validación `@Min(0)` en el DTO | Agrégalo al campo `votos` en `ActaDetalleDto` |
