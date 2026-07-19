# T-BE-11 — Endpoints de Actas (OBSERVER)

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-BE-06, T-BE-09

---

## ¿Qué vas a construir?

Los endpoints que usa el OBSERVER para registrar actas:
- Registrar una acta en cualquier JRV (sin pre-asignación)
- Ver cargos aplicables para una JRV específica
- Ver sus propias actas (filtro RLS: registrada_por = user_id)
- Ver actas EN_DISPUTA que debe corregir
- Corregir datos de acta propia
- Subir la imagen del acta física

---

## Conceptos clave

### SecurityContextHolder — obtener el usuario autenticado
El filtro JWT pone el `userId` en el contexto de seguridad. Cualquier controller puede recuperarlo:

```java
Long userId = (Long) SecurityContextHolder.getContext()
    .getAuthentication()
    .getPrincipal();
```

### @PathVariable vs @RequestParam
```java
// @PathVariable: va en la URL /api/actas/42
@GetMapping("/actas/{id}")
public Acta getActa(@PathVariable Long id) { }

// @RequestParam: va como query param /api/buscar?estado=ENVIADA
@GetMapping("/buscar")
public List<Acta> buscar(@RequestParam(required = false) String estado) { }
```

### MultipartFile — subir archivos
Para recibir una imagen en el backend:
```java
@PostMapping(consumes = "multipart/form-data")
public void subirImagen(@RequestParam("imagen") MultipartFile archivo) {
    byte[] bytes = archivo.getBytes();
    // ...
}
```

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/
  dto/
    JrvConEstadoDto.java      ← JRV + cuántos cargos pendientes
    CargoConEstadoDto.java    ← Cargo + si ya tiene acta registrada
  controller/
    ObserverController.java
```

---

## Pasos

### Paso 1 — DTOs de respuesta

```java
// src/main/java/ec/twintailcs/electoral/dto/JrvConEstadoDto.java
package ec.twintailcs.electoral.dto;

public class JrvConEstadoDto {
    private Long id;
    private String numero;      // número de la JRV
    private String parroquia;
    private int cargosPendientes;
    private int cargosRegistrados;

    // Constructor
    public JrvConEstadoDto(Long id, String numero, String parroquia,
                            int cargosPendientes, int cargosRegistrados) {
        this.id = id;
        this.numero = numero;
        this.parroquia = parroquia;
        this.cargosPendientes = cargosPendientes;
        this.cargosRegistrados = cargosRegistrados;
    }

    // Getters
    public Long getId() { return id; }
    public String getNumero() { return numero; }
    public String getParroquia() { return parroquia; }
    public int getCargosPendientes() { return cargosPendientes; }
    public int getCargosRegistrados() { return cargosRegistrados; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/CargoConEstadoDto.java
package ec.twintailcs.electoral.dto;

import ec.twintailcs.electoral.domain.EstadoActa;

public class CargoConEstadoDto {
    private Long id;
    private String nombre;
    private String nivel;
    private boolean registrado;      // ¿ya existe acta para este cargo en esta JRV?
    private EstadoActa estadoActa;   // null si no está registrado
    private Long actaId;             // null si no está registrado

    // Constructor para cargo sin acta
    public CargoConEstadoDto(Long id, String nombre, String nivel) {
        this.id = id;
        this.nombre = nombre;
        this.nivel = nivel;
        this.registrado = false;
    }

    // Constructor para cargo con acta
    public CargoConEstadoDto(Long id, String nombre, String nivel,
                              EstadoActa estadoActa, Long actaId) {
        this.id = id;
        this.nombre = nombre;
        this.nivel = nivel;
        this.registrado = true;
        this.estadoActa = estadoActa;
        this.actaId = actaId;
    }

    // Getters
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getNivel() { return nivel; }
    public boolean isRegistrado() { return registrado; }
    public EstadoActa getEstadoActa() { return estadoActa; }
    public Long getActaId() { return actaId; }
}
```

### Paso 2 — ObserverController

```java
// src/main/java/ec/twintailcs/electoral/controller/ObserverController.java
package ec.twintailcs.electoral.controller;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.repository.*;
import ec.twintailcs.electoral.service.ActaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/observer")
@PreAuthorize("hasRole('OBSERVER')")
public class ObserverController {

    private final ActaService actaService;
    private final ActaRepository actaRepository;
    private final UsuarioRepository usuarioRepository;

    public ObserverController(ActaService actaService,
                               ActaRepository actaRepository,
                               UsuarioRepository usuarioRepository) {
        this.actaService = actaService;
        this.actaRepository = actaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    private Long getObserverId() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * GET /api/observer/mis-actas
     * Retorna actas registradas por el OBSERVER autenticado (RLS filter).
     * Parámetro opcional: ?estado=ENVIADA
     */
    @GetMapping("/mis-actas")
    public ResponseEntity<List<Acta>> misActas(@RequestParam(required = false) EstadoActa estado) {
        Long observerId = getObserverId();
        Usuario observer = usuarioRepository.findById(observerId).orElseThrow();
        
        List<Acta> resultado;
        if (estado != null) {
            resultado = actaRepository.findByRegistrada_porAndEstado(observer, estado);
        } else {
            resultado = actaRepository.findByRegistrada_por(observer);
        }
        
        return ResponseEntity.ok(resultado);
    }

    /**
     * GET /api/observer/correcciones-pendientes
     * Actas EN_DISPUTA registradas por este OBSERVER que necesitan corrección.
     */
    @GetMapping("/correcciones-pendientes")
    public ResponseEntity<List<Acta>> correccionesPendientes() {
        Long observerId = getObserverId();
        Usuario observer = usuarioRepository.findById(observerId).orElseThrow();
        List<Acta> resultado = actaRepository.findByRegistrada_porAndEstado(observer, EstadoActa.EN_DISPUTA);
        return ResponseEntity.ok(resultado);
    }

    /**
     * POST /api/actas
     * Registrar una nueva acta. Solo OBSERVER.
     */
    @PostMapping("/actas")
    public ResponseEntity<Acta> registrarActa(@Valid @RequestBody ActaRequestDto dto,
                                               HttpServletRequest request) {
        Long observerId = getObserverId();
        Acta acta = actaService.registrar(dto, observerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(acta);
    }

    /**
     * PUT /api/observer/actas/{actaId}/corregir
     * Observer corrige su propia acta EN_DISPUTA.
     */
    @PutMapping("/actas/{actaId}/corregir")
    public ResponseEntity<Acta> corregirActa(@PathVariable Long actaId,
                                              @Valid @RequestBody ActaRequestDto dto,
                                              HttpServletRequest request) {
        Long observerId = getObserverId();
        // false = no es coordinator, es observer
        Acta acta = actaService.corregir(actaId, dto, observerId, false, request);
        return ResponseEntity.ok(acta);
    }

    /**
     * POST /api/observer/actas/{actaId}/imagen
     * Subir imagen del acta. Recibe archivo multipart.
     */
    @PostMapping("/actas/{actaId}/imagen")
    public ResponseEntity<Void> subirImagen(@PathVariable Long actaId,
                                             @RequestParam("imagen") MultipartFile imagen,
                                             HttpServletRequest request) {
        // Por ahora solo guardamos el nombre — en T-BE-14 conectamos Supabase Storage
        // TODO: llamar storageService.upload(imagen.getBytes(), filename)
        return ResponseEntity.ok().build();
    }
}
```

---

## Cómo verificar que funciona

```bash
# 1. Login como observer
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"observer@electoral.ec","password":"Password123"}' \
  | jq -r '.accessToken')

# 2. Ver mis actas (propias, RLS filter)
curl http://localhost:8080/api/observer/mis-actas \
  -H "Authorization: Bearer $TOKEN"

# 3. Ver cargos de la JRV 1 (endpoint público, sin restricción)
curl http://localhost:8080/api/jrv/1/cargos \
  -H "Authorization: Bearer $TOKEN"

# 4. Registrar una acta (cualquier JRV, se guarda registrada_por = user_id)
curl -X POST http://localhost:8080/api/actas \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "jrvId": 1, "cargoId": 1,
    "totalSufragantes": 50,
    "votosBlancos": 2, "votosNulos": 1,
    "detalles": [
      {"candidatoId": 1, "votos": 30},
      {"candidatoId": 2, "votos": 17}
    ]
  }'
```

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `403` al ver acta | RLS: acta registrada por otro OBSERVER | Verifica `registrada_por = current_user_id` |
| `409 Conflict` | Ya existe acta activa para esa JRV+cargo | Es correcto — usa locking pesimista |
| `422` en votos | Los votos no suman al total de sufragantes | Verifica: 30 + 17 + 2 + 1 = 50 ✓ |
| `MultipartFile` no llega | Falta `consumes = multipart/form-data` | Agrega `consumes` o usa `@RequestPart` |
