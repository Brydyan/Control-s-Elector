# T-BE-13 — Dashboard Endpoints (ADMIN + MASTER + COORDINATOR)

**Estado:** ❌ **NO IMPLEMENTADO** — Falta DashboardController. Lógica de queries puede reutilizarse del doc.

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-BE-12

---

## ⚠️ Nota de alineación con código actual

**Ya existe:**
- ActaRepository, ActaDetalleRepository con métodos `findAll()`, `countByEstado()`
- Frontend DashboardPage con placeholder data (necesita HTTP calls)

**Falta:**
- `DashboardController` con endpoints `/api/v1/dashboard/resumen`, `/resultados`, `/progreso`, `/novedades`
- DTOs de respuesta (ResumenActasDto, ResultadoCargoDto, ProgresoCantonDto, NovedadDto)
- Queries JPQL para agregaciones (GROUP BY, SUM)

**Nota adicional:** Endpoint `/novedades` (para ADMIN) debe listar actas en EN_DISPUTA + evidencias con observaciones marcadas (T-BE-16). Ver sección Paso 4 del doc.

---

## ¿Qué vas a construir?

Endpoints de solo lectura para el dashboard:
- Resumen de actas por estado
- Resultados de votos por cargo y cantón
- Progreso de registro por cantón (JRVs registradas vs total)

**Regla importante:** Las actas ANULADAS NO se incluyen en ningún cálculo del dashboard.

---

## Conceptos clave

### Interface Projection en Spring Data
Permite retornar solo ciertos campos de una query sin crear una clase completa:

```java
// Interfaz con getters — Spring la implementa automáticamente
public interface VotosPorCandidato {
    String getCandidatoNombre();
    String getPartidoSiglas();
    Long getTotalVotos();
}

// En el repository:
@Query("SELECT d.candidato.nombre as candidatoNombre, ...")
List<VotosPorCandidato> obtenerResultados();
```

### GROUP BY en JPQL
Para sumar votos agrupados por candidato:
```sql
SELECT d.candidato.id, SUM(d.votos) as total
FROM ActaDetalle d
WHERE d.acta.estado != 'ANULADA'
GROUP BY d.candidato.id
```

### ADMIN — solo lectura
ADMIN no puede modificar nada. Todos sus endpoints son `GET`. Verificamos esto por diseño — solo exponemos GETs en el controller del dashboard.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/
  dto/
    ResumenActasDto.java
    ResultadoCargoDto.java
    ProgresoCantonDto.java
  controller/
    DashboardController.java
```

Además, agrega queries en:
```
ActaRepository.java       ← queries de dashboard
ActaDetalleRepository.java ← queries de votos
```

---

## Pasos

### Paso 1 — DTOs de respuesta del dashboard

```java
// src/main/java/ec/twintailcs/electoral/dto/ResumenActasDto.java
package ec.twintailcs.electoral.dto;

public class ResumenActasDto {
    private long enviadas;
    private long enDisputa;
    private long corregidas;
    private long aprobadas;
    private long totalActivas; // suma de todos (sin ANULADAS)

    public ResumenActasDto(long enviadas, long enDisputa, long corregidas, long aprobadas) {
        this.enviadas = enviadas;
        this.enDisputa = enDisputa;
        this.corregidas = corregidas;
        this.aprobadas = aprobadas;
        this.totalActivas = enviadas + enDisputa + corregidas + aprobadas;
    }

    // Getters
    public long getEnviadas() { return enviadas; }
    public long getEnDisputa() { return enDisputa; }
    public long getCorregidas() { return corregidas; }
    public long getAprobadas() { return aprobadas; }
    public long getTotalActivas() { return totalActivas; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/ResultadoCargoDto.java
package ec.twintailcs.electoral.dto;

public class ResultadoCargoDto {
    private Long cargoId;
    private String cargoNombre;
    private Long candidatoId;
    private String candidatoNombre;
    private String partidoSiglas;
    private Long totalVotos;

    // Constructor completo
    public ResultadoCargoDto(Long cargoId, String cargoNombre, Long candidatoId,
                              String candidatoNombre, String partidoSiglas, Long totalVotos) {
        this.cargoId = cargoId;
        this.cargoNombre = cargoNombre;
        this.candidatoId = candidatoId;
        this.candidatoNombre = candidatoNombre;
        this.partidoSiglas = partidoSiglas;
        this.totalVotos = totalVotos;
    }

    // Getters
    public Long getCargoId() { return cargoId; }
    public String getCargoNombre() { return cargoNombre; }
    public Long getCandidatoId() { return candidatoId; }
    public String getCandidatoNombre() { return candidatoNombre; }
    public String getPartidoSiglas() { return partidoSiglas; }
    public Long getTotalVotos() { return totalVotos; }
}
```

```java
// src/main/java/ec/twintailcs/electoral/dto/ProgresoCantonDto.java
package ec.twintailcs.electoral.dto;

public class ProgresoCantonDto {
    private Long cantonId;
    private String cantonNombre;
    private long totalJrvs;
    private long jrvsConActa;   // JRVs que tienen AL MENOS UNA acta registrada
    private double porcentaje;

    public ProgresoCantonDto(Long cantonId, String cantonNombre,
                              long totalJrvs, long jrvsConActa) {
        this.cantonId = cantonId;
        this.cantonNombre = cantonNombre;
        this.totalJrvs = totalJrvs;
        this.jrvsConActa = jrvsConActa;
        // Calcular porcentaje, evitando división por cero
        this.porcentaje = totalJrvs > 0 ? (jrvsConActa * 100.0 / totalJrvs) : 0;
    }

    // Getters
    public Long getCantonId() { return cantonId; }
    public String getCantonNombre() { return cantonNombre; }
    public long getTotalJrvs() { return totalJrvs; }
    public long getJrvsConActa() { return jrvsConActa; }
    public double getPorcentaje() { return porcentaje; }
}
```

### Paso 2 — Queries en los repositorios

En `ActaDetalleRepository.java`, agrega:

```java
/**
 * Suma de votos por candidato en actas NO anuladas.
 * Se usa para los resultados del dashboard.
 */
@Query("""
    SELECT new ec.twintailcs.electoral.dto.ResultadoCargoDto(
        d.candidato.cargo.id,
        d.candidato.cargo.nombre,
        d.candidato.id,
        d.candidato.nombre,
        d.candidato.partido.siglas,
        SUM(d.votos)
    )
    FROM ActaDetalle d
    WHERE d.acta.estado != 'ANULADA'
    GROUP BY d.candidato.cargo.id, d.candidato.cargo.nombre,
             d.candidato.id, d.candidato.nombre, d.candidato.partido.siglas
    ORDER BY d.candidato.cargo.id, SUM(d.votos) DESC
    """)
List<ResultadoCargoDto> obtenerResultadosPorCandidato();
```

En `ActaRepository.java`, agrega:

```java
/**
 * Contar cuántas JRVs distintas tienen al menos un acta activa (no anulada)
 * por cantón.
 */
@Query("""
    SELECT a.jrv.parroquia.canton.id as cantonId,
           COUNT(DISTINCT a.jrv.id) as jrvsConActa
    FROM Acta a
    WHERE a.estado != 'ANULADA'
    GROUP BY a.jrv.parroquia.canton.id
    """)
List<Object[]> contarJrvsConActaPorCanton();
```

### Paso 3 — DashboardController

```java
// src/main/java/ec/twintailcs/electoral/controller/DashboardController.java
package ec.twintailcs.electoral.controller;

import ec.twintailcs.electoral.domain.*;
import ec.twintailcs.electoral.dto.*;
import ec.twintailcs.electoral.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER', 'COORDINATOR')")
public class DashboardController {

    private final ActaRepository actaRepository;
    private final ActaDetalleRepository actaDetalleRepository;
    private final JrvRepository jrvRepository;
    private final CantonRepository cantonRepository;

    public DashboardController(ActaRepository actaRepository,
                                ActaDetalleRepository actaDetalleRepository,
                                JrvRepository jrvRepository,
                                CantonRepository cantonRepository) {
        this.actaRepository = actaRepository;
        this.actaDetalleRepository = actaDetalleRepository;
        this.jrvRepository = jrvRepository;
        this.cantonRepository = cantonRepository;
    }

    /**
     * GET /api/dashboard/resumen
     * Conteo de actas por estado (sin ANULADAS ni BORRADOR).
     */
    @GetMapping("/resumen")
    public ResponseEntity<ResumenActasDto> resumen() {
        long enviadas = actaRepository.countByEstado(EstadoActa.ENVIADA);
        long enDisputa = actaRepository.countByEstado(EstadoActa.EN_DISPUTA);
        long corregidas = actaRepository.countByEstado(EstadoActa.CORREGIDA);
        long aprobadas = actaRepository.countByEstado(EstadoActa.APROBADA);

        return ResponseEntity.ok(new ResumenActasDto(enviadas, enDisputa, corregidas, aprobadas));
    }

    /**
     * GET /api/dashboard/resultados
     * Votos totales por candidato, agrupados por cargo.
     * Excluye actas ANULADAS.
     */
    @GetMapping("/resultados")
    public ResponseEntity<List<ResultadoCargoDto>> resultados() {
        List<ResultadoCargoDto> resultados = actaDetalleRepository.obtenerResultadosPorCandidato();
        return ResponseEntity.ok(resultados);
    }

    /**
     * GET /api/dashboard/progreso
     * Por cada cantón: cuántas JRVs tienen acta registrada vs el total.
     */
    @GetMapping("/progreso")
    public ResponseEntity<List<ProgresoCantonDto>> progreso() {
        // Total de JRVs por cantón
        List<Canton> cantones = cantonRepository.findAll();

        // JRVs con acta por cantón (de la BD)
        List<Object[]> conActa = actaRepository.contarJrvsConActaPorCanton();

        // Convertir la lista de Object[] a un mapa cantonId → cantidad
        Map<Long, Long> conActaMap = new HashMap<>();
        for (Object[] fila : conActa) {
            Long cantonId = (Long) fila[0];
            Long cantidad = (Long) fila[1];
            conActaMap.put(cantonId, cantidad);
        }

        List<ProgresoCantonDto> resultado = new ArrayList<>();
        for (Canton canton : cantones) {
            long totalJrvs = jrvRepository.countByParroquia_Canton(canton);
            long jrvsConActa = conActaMap.getOrDefault(canton.getId(), 0L);

            resultado.add(new ProgresoCantonDto(
                canton.getId(),
                canton.getNombre(),
                totalJrvs,
                jrvsConActa
            ));
        }

        return ResponseEntity.ok(resultado);
    }
}
```

---

## Cómo verificar que funciona

```bash
# Resumen (con token de ADMIN, MASTER o COORDINATOR)
curl http://localhost:8080/api/dashboard/resumen \
  -H "Authorization: Bearer TOKEN"
# Respuesta: {"enviadas":5,"enDisputa":1,"corregidas":2,"aprobadas":3,"totalActivas":11}

# Resultados de votos
curl http://localhost:8080/api/dashboard/resultados \
  -H "Authorization: Bearer TOKEN"

# Progreso por cantón
curl http://localhost:8080/api/dashboard/progreso \
  -H "Authorization: Bearer TOKEN"
```

---

---

## Paso 4 — Endpoint de Novedades (ADMIN) — Actas en disputa + Evidencias con observaciones

**Novedad:** Un endpoint adicional específico para ADMIN que lista actas que requieren atención urgente (EN_DISPUTA sin resolver) + evidencias que han sido marcadas con observaciones, para usar como base de reclamos.

Agrega al `DashboardController`:

```java
/**
 * GET /api/dashboard/novedades
 * Solo ADMIN/MASTER/COORDINATOR — lista actas en EN_DISPUTA + evidencias
 * con observaciones marcadas. Incluye tiempo transcurrido para urgencia.
 *
 * Respuesta: List<NovedadDto>
 */
@GetMapping("/novedades")
public ResponseEntity<List<NovedadDto>> novedades() {
    // 1. Actas en EN_DISPUTA (sin resolver)
    List<Acta> actasEnDisputa = actaRepository.findByEstado(EstadoActa.EN_DISPUTA);
    
    // 2. Evidencias con observaciones marcadas (campo/relación a ser definida en T-BE-16)
    // List<Evidencia> evidenciasConObs = evidenciaRepository.findByObservacionesIsNotNull();
    
    // 3. Mapear ambas a un DTO unificado de "novedad"
    List<NovedadDto> novedades = new ArrayList<>();
    for (Acta acta : actasEnDisputa) {
        long minutosTranscurridos = Duration.between(
            acta.getRegistradaEn().atZone(ZoneId.systemDefault()),
            Instant.now().atZone(ZoneId.systemDefault())
        ).toMinutes();
        
        novedades.add(new NovedadDto(
            acta.getId(),
            acta.getPublicId(),
            "ACTA_EN_DISPUTA",
            acta.getJrv().getId(),
            acta.getCargo().getNombre(),
            acta.getRegistrada_por().getNombre() + " " + acta.getRegistrada_por().getApellido(),
            minutosTranscurridos,
            acta.getMotivo_disputa()
        ));
    }
    
    return ResponseEntity.ok(novedades);
}
```

Crear DTO:

```java
// src/main/java/ec/twintailcs/electoral/dto/NovedadDto.java
package ec.twintailcs.electoral.dto;

import java.util.UUID;

public class NovedadDto {
    private Long actaId;
    private UUID actaPublicId;
    private String tipo;              // "ACTA_EN_DISPUTA", "EVIDENCIA_CON_OBS"
    private Long jrvId;
    private String cargo;
    private String observer;          // nombre + apellido
    private long minutosTranscurridos;
    private String motivo;            // motivo de disputa o descripción observación

    public NovedadDto(Long actaId, UUID actaPublicId, String tipo, Long jrvId,
                     String cargo, String observer, long minutosTranscurridos, String motivo) {
        this.actaId = actaId;
        this.actaPublicId = actaPublicId;
        this.tipo = tipo;
        this.jrvId = jrvId;
        this.cargo = cargo;
        this.observer = observer;
        this.minutosTranscurridos = minutosTranscurridos;
        this.motivo = motivo;
    }

    // Getters
    public Long getActaId() { return actaId; }
    public UUID getActaPublicId() { return actaPublicId; }
    public String getTipo() { return tipo; }
    public Long getJrvId() { return jrvId; }
    public String getCargo() { return cargo; }
    public String getObserver() { return observer; }
    public long getMinutosTranscurridos() { return minutosTranscurridos; }
    public String getMotivo() { return motivo; }
}
```

**Referencia cruzada:** El concepto de "tiempo transcurrido" para marcar urgencia aparece ya en `documents/mockups/COORDINATOR/desktop/05-disputas.html` (clases `.urgente-tag` y `.disputa-tag`) — ADMIN reutiliza el mismo criterio.

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `403` con ADMIN | `hasAnyRole` no incluye ADMIN | Verifica que el string es exactamente `'ADMIN'` (mayúsculas) |
| Actas anuladas aparecen | Falta el filtro en la query | Agrega `WHERE estado != 'ANULADA'` en la query JPQL |
| `ClassCastException` en Object[] | Tipos de la query no coinciden | Verifica que los tipos de los campos en el SELECT coinciden con el DTO |
| Porcentaje siempre 0 | División entera en Java | Usa `100.0` (double) en vez de `100` (int) en el cálculo |
