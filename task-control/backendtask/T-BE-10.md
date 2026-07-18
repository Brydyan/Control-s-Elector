# T-BE-10 — Endpoints de Configuración Preelectoral (MASTER)

**Dificultad:** Principiante-Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-BE-09

---

## ¿Qué vas a construir?

Los endpoints que usa el MASTER para configurar el sistema antes de las elecciones:
- Registrar los cargos (Alcalde, Prefecto, etc.)
- Registrar los partidos políticos
- Registrar los candidatos (vinculados a cargo + partido)
- Asignar qué cargos aplican en cada JRV
- Crear usuarios del sistema
- Asignar JRVs a observers

---

## Conceptos clave

### DTO vs Entidad — ¿por qué no exponer la entidad directamente?
Si expones la entidad JPA directamente en el controller, el cliente puede ver campos internos (como `passwordHash`). Los DTOs te permiten controlar exactamente qué entra y qué sale.

```
Entidad (BD) ←→ DTO (API)
  passwordHash      ✗ no se expone
  nombre            ✓ se expone
  email             ✓ se expone
```

### @PreAuthorize("hasRole('MASTER')")
Verifica el rol ANTES de ejecutar el método. Si el usuario no es MASTER, Spring retorna automáticamente `403 Forbidden`. Funciona gracias a `@EnableMethodSecurity` en SecurityConfig.

### @Valid + @RequestBody
`@RequestBody` deserializa el JSON del request a tu DTO. `@Valid` activa las validaciones (`@NotBlank`, `@NotNull`, etc.) automáticamente.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/
  dto/
    CargoDto.java
    PartidoDto.java
    CandidatoDto.java
    UsuarioCreateDto.java
  service/
    ConfiguracionService.java
  controller/
    ConfiguracionController.java
    UsuarioController.java
```

---

## Pasos

### Paso 1 — DTOs

```java
// src/main/java/ec/twintailcs/electoral/dto/CargoDto.java
package ec.twintailcs.electoral.dto;

import ec.twintailcs.electoral.domain.NivelCargo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CargoDto {

    @NotBlank(message = "El nombre del cargo es obligatorio")
    private String nombre;

    @NotNull(message = "El nivel es obligatorio")
    private NivelCargo nivel; // NACIONAL, PROVINCIA, CANTON, PARROQUIA

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public NivelCargo getNivel() { return nivel; }
    public void setNivel(NivelCargo nivel) { this.nivel = nivel; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/PartidoDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.NotBlank;

public class PartidoDto {

    @NotBlank
    private String nombre;

    @NotBlank
    private String siglas;  // ej: "PSC", "AP"

    private String color;   // ej: "#FF0000" — opcional

    // Getters y Setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/CandidatoDto.java
package ec.twintailcs.electoral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CandidatoDto {

    @NotBlank
    private String nombre;

    @NotNull(message = "El cargoId es obligatorio")
    private Long cargoId;

    @NotNull(message = "El partidoId es obligatorio")
    private Long partidoId;

    // Getters y Setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
    public Long getPartidoId() { return partidoId; }
    public void setPartidoId(Long partidoId) { this.partidoId = partidoId; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/UsuarioCreateDto.java
package ec.twintailcs.electoral.dto;

import ec.twintailcs.electoral.domain.RolUsuario;
import jakarta.validation.constraints.*;

public class UsuarioCreateDto {

    @NotBlank
    private String nombre;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotNull
    private RolUsuario rol;

    // Getters y Setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public RolUsuario getRol() { return rol; }
    public void setRol(RolUsuario rol) { this.rol = rol; }
}
```

### Paso 2 — ConfiguracionService

```java
// src/main/java/ec/twintailcs/electoral/service/ConfiguracionService.java
package ec.twintailcs.electoral.service;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConfiguracionService {

    private final CargoRepository cargoRepository;
    private final PartidoRepository partidoRepository;
    private final CandidatoRepository candidatoRepository;
    private final JrvRepository jrvRepository;
    private final JrvCargoRepository jrvCargoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditLogService auditLogService;

    // Constructor con todas las dependencias
    public ConfiguracionService(CargoRepository cargoRepository,
                                PartidoRepository partidoRepository,
                                CandidatoRepository candidatoRepository,
                                JrvRepository jrvRepository,
                                JrvCargoRepository jrvCargoRepository,
                                UsuarioRepository usuarioRepository,
                                AuditLogService auditLogService) {
        this.cargoRepository = cargoRepository;
        this.partidoRepository = partidoRepository;
        this.candidatoRepository = candidatoRepository;
        this.jrvRepository = jrvRepository;
        this.jrvCargoRepository = jrvCargoRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Cargo crearCargo(CargoDto dto, Long masterId) {
        Cargo cargo = new Cargo();
        cargo.setNombre(dto.getNombre());
        cargo.setNivel(dto.getNivel());
        Cargo guardado = cargoRepository.save(cargo);

        auditLogService.registrar(masterId, "CREAR_CARGO", "cargo", guardado.getId(),
            "Cargo creado: " + dto.getNombre());
        return guardado;
    }

    @Transactional
    public Partido crearPartido(PartidoDto dto, Long masterId) {
        Partido partido = new Partido();
        partido.setNombre(dto.getNombre());
        partido.setSiglas(dto.getSiglas());
        partido.setColor(dto.getColor());
        Partido guardado = partidoRepository.save(partido);

        auditLogService.registrar(masterId, "CREAR_PARTIDO", "partido", guardado.getId(),
            "Partido creado: " + dto.getSiglas());
        return guardado;
    }

    @Transactional
    public Candidato crearCandidato(CandidatoDto dto, Long masterId) {
        Cargo cargo = cargoRepository.findById(dto.getCargoId())
            .orElseThrow(() -> new RuntimeException("Cargo no encontrado: " + dto.getCargoId()));

        Partido partido = partidoRepository.findById(dto.getPartidoId())
            .orElseThrow(() -> new RuntimeException("Partido no encontrado: " + dto.getPartidoId()));

        Candidato candidato = new Candidato();
        candidato.setNombre(dto.getNombre());
        candidato.setCargo(cargo);
        candidato.setPartido(partido);
        Candidato guardado = candidatoRepository.save(candidato);

        auditLogService.registrar(masterId, "CREAR_CANDIDATO", "candidato", guardado.getId(),
            "Candidato: " + dto.getNombre() + " para " + cargo.getNombre());
        return guardado;
    }

    @Transactional
    public void asignarCargoAJrv(Long jrvId, Long cargoId, Long masterId) {
        Jrv jrv = jrvRepository.findById(jrvId)
            .orElseThrow(() -> new RuntimeException("JRV no encontrada: " + jrvId));

        Cargo cargo = cargoRepository.findById(cargoId)
            .orElseThrow(() -> new RuntimeException("Cargo no encontrado: " + cargoId));

        JrvCargo jrvCargo = new JrvCargo();
        jrvCargo.setJrv(jrv);
        jrvCargo.setCargo(cargo);
        jrvCargoRepository.save(jrvCargo);

        auditLogService.registrar(masterId, "ASIGNAR_CARGO_JRV", "jrv_cargo", null,
            "Cargo " + cargoId + " asignado a JRV " + jrvId);
    }

    public List<Cargo> listarCargos() {
        return cargoRepository.findAll();
    }

    public List<Partido> listarPartidos() {
        return partidoRepository.findAll();
    }
}
```

### Paso 3 — ConfiguracionController

```java
// src/main/java/ec/twintailcs/electoral/controller/ConfiguracionController.java
package ec.twintailcs.electoral.controller;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.service.ConfiguracionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('MASTER')")  // Todos los endpoints de esta clase son solo para MASTER
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    // ─── Helper para obtener userId del token ────────────────────────────────
    private Long getMasterId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    // ─── Cargos ──────────────────────────────────────────────────────────────

    @GetMapping("/cargos")
    @PreAuthorize("isAuthenticated()")  // Cualquier usuario autenticado puede ver la lista
    public ResponseEntity<List<Cargo>> listarCargos() {
        return ResponseEntity.ok(configuracionService.listarCargos());
    }

    @PostMapping("/cargos")
    public ResponseEntity<Cargo> crearCargo(@Valid @RequestBody CargoDto dto) {
        Cargo cargo = configuracionService.crearCargo(dto, getMasterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(cargo);
    }

    // ─── Partidos ─────────────────────────────────────────────────────────────

    @GetMapping("/partidos")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Partido>> listarPartidos() {
        return ResponseEntity.ok(configuracionService.listarPartidos());
    }

    @PostMapping("/partidos")
    public ResponseEntity<Partido> crearPartido(@Valid @RequestBody PartidoDto dto) {
        Partido partido = configuracionService.crearPartido(dto, getMasterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(partido);
    }

    // ─── Candidatos ───────────────────────────────────────────────────────────

    @PostMapping("/candidatos")
    public ResponseEntity<Candidato> crearCandidato(@Valid @RequestBody CandidatoDto dto) {
        Candidato candidato = configuracionService.crearCandidato(dto, getMasterId());
        return ResponseEntity.status(HttpStatus.CREATED).body(candidato);
    }

    // ─── JRV-Cargo ────────────────────────────────────────────────────────────

    @PostMapping("/jrvs/{jrvId}/cargos/{cargoId}")
    public ResponseEntity<Void> asignarCargoAJrv(@PathVariable Long jrvId,
                                                   @PathVariable Long cargoId) {
        configuracionService.asignarCargoAJrv(jrvId, cargoId, getMasterId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
```

---

## Cómo verificar que funciona

Con token de MASTER:
```bash
# Crear un cargo
curl -X POST http://localhost:8080/api/cargos \
  -H "Authorization: Bearer TOKEN_MASTER" \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Alcalde/sa", "nivel": "CANTON"}'

# Crear un partido
curl -X POST http://localhost:8080/api/partidos \
  -H "Authorization: Bearer TOKEN_MASTER" \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Partido Social Cristiano", "siglas": "PSC", "color": "#0000FF"}'
```

Con token de OBSERVER (debe dar 403):
```bash
curl -X POST http://localhost:8080/api/cargos \
  -H "Authorization: Bearer TOKEN_OBSERVER" \
  -H "Content-Type: application/json" \
  -d '{"nombre": "Test", "nivel": "CANTON"}'
# Debe retornar: 403 Forbidden
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `403` con token de MASTER | `@EnableMethodSecurity` no activo | Verifica que SecurityConfig tiene `@EnableMethodSecurity` |
| `400` al crear candidato | `cargoId` o `partidoId` no existe en BD | Primero crea el cargo y el partido, luego el candidato |
| Entidad se expone con campos internos | Estás retornando la entidad directamente | Crea un DTO de respuesta o usa `@JsonIgnore` en campos sensibles |
