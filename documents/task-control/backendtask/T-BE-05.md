# T-BE-05 — AuditLogService

**Dificultad:** Principiante
**Tiempo estimado:** 1 hora
**Depende de:** T-BE-04

---

## ¿Qué vas a construir?

Un servicio centralizado que registra **toda acción importante** del sistema en la tabla `audit_log`. Cada vez que alguien registra un acta, la anula, hace login, o cambia un estado — queda registrado: quién, qué, cuándo y desde qué IP.

**Regla de oro del proyecto:** El flujo siempre es `API → AuditLogService → JPA → PostgreSQL`. Nunca se escribe en `audit_log` directamente desde un controller.

---

## Conceptos clave

### @Service
Marca la clase como un componente de la capa de lógica de negocio. Spring la registra automáticamente y permite inyectarla en otros lugares.

### Inyección por constructor (buena práctica)
Hay dos formas de inyectar dependencias:

```java
// ❌ Forma antigua — campo con @Autowired (evitar)
@Autowired
private AuditLogRepository repository;

// ✅ Forma recomendada — inyección por constructor
private final AuditLogRepository repository;

public AuditLogService(AuditLogRepository repository) {
    this.repository = repository;
}
```

¿Por qué preferir constructor? Porque:
- Hace el código testeable fácilmente
- El campo puede ser `final` (no se puede reasignar accidentalmente)
- Es explícito — se ve claramente qué necesita la clase para funcionar

### HttpServletRequest
Es el objeto que representa la petición HTTP que llegó al servidor. Lo usamos para extraer la IP del cliente con `request.getRemoteAddr()`.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/service/
  AuditLogService.java
```

---

## Sugerencia de código completa

```java
// src/main/java/ec/twintailcs/electoral/service/AuditLogService.java
package ec.twintailcs.electoral.service;

import ec.twintailcs.electoral.domain.AuditLog;
import ec.twintailcs.electoral.domain.Usuario;
import ec.twintailcs.electoral.repository.AuditLogRepository;
import ec.twintailcs.electoral.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    // Inyección por constructor — forma recomendada
    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           UsuarioRepository usuarioRepository) {
        this.auditLogRepository = auditLogRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Registra una acción en el log de auditoría.
     *
     * Ejemplo de uso:
     *   auditLogService.registrar(usuarioId, "REGISTRO_ACTA", "acta", acta.getId(),
     *       "Observer registró acta para JRV 42, cargo Alcalde", request);
     *
     * @param usuarioId  ID del usuario que realizó la acción (null si es el sistema)
     * @param accion     Qué hizo: "REGISTRO_ACTA", "LOGIN", "ANULACION_ACTA", etc.
     * @param entidad    En qué tabla: "acta", "usuario", "partido"
     * @param entidadId  ID del registro afectado
     * @param detalle    Descripción legible de lo que pasó
     * @param request    La petición HTTP (para extraer la IP)
     */
    @Transactional
    public void registrar(Long usuarioId,
                          String accion,
                          String entidad,
                          Long entidadId,
                          String detalle,
                          HttpServletRequest request) {

        AuditLog log = new AuditLog();

        // Si hay usuarioId, buscamos el usuario; si no, dejamos null
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(log::setUsuario);
        }

        log.setAccion(accion);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setDetalle(detalle);

        // Extraer IP del request
        // Tip: Si hay un proxy (Nginx, Railway), la IP real viene en el header X-Forwarded-For
        String ip = obtenerIpReal(request);
        log.setIpAddress(ip);

        // @CreationTimestamp en la entidad llenará el timestamp automáticamente
        auditLogRepository.save(log);
    }

    /**
     * Versión simplificada sin HttpServletRequest (para operaciones del sistema)
     */
    @Transactional
    public void registrar(Long usuarioId, String accion, String entidad, Long entidadId, String detalle) {
        registrar(usuarioId, accion, entidad, entidadId, detalle, null);
    }

    /**
     * Extrae la IP real del cliente.
     * Cuando hay un proxy (Railway, Nginx), la IP real viene en X-Forwarded-For.
     */
    private String obtenerIpReal(HttpServletRequest request) {
        if (request == null) return "sistema";

        // Primero intentamos con el header del proxy
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For puede tener múltiples IPs: "IP_cliente, IP_proxy1, IP_proxy2"
            // La primera es la del cliente real
            return xForwardedFor.split(",")[0].trim();
        }

        // Si no hay proxy, usamos la IP directa
        return request.getRemoteAddr();
    }
}
```

---

## Cómo usar AuditLogService en otro service

```java
// Ejemplo en ActaService (T-BE-06)
@Service
public class ActaService {

    private final ActaRepository actaRepository;
    private final AuditLogService auditLogService; // ← inyectar aquí

    // Constructor con ambas dependencias
    public ActaService(ActaRepository actaRepository, AuditLogService auditLogService) {
        this.actaRepository = actaRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Acta registrar(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
        // ... lógica del acta ...
        Acta acta = actaRepository.save(nuevaActa);

        // SIEMPRE al final, registrar en audit log
        auditLogService.registrar(
            observerId,
            "REGISTRO_ACTA",
            "acta",
            acta.getId(),
            "Acta registrada para JRV " + dto.getJrvId() + ", cargo " + dto.getCargoId(),
            request
        );

        return acta;
    }
}
```

---

## Cómo verificar que funciona

1. Corre la app: `./mvnw spring-boot:run`
2. Haz un POST de prueba (cuando tengas el controller listo)
3. Verifica en la BD:
```sql
SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT 10;
```
Deberías ver registros con accion, entidad, ip y timestamp.

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `NullPointerException` en `registrar()` | `request` es null y no lo verificas | Usa la versión sobrecargada sin request, o agrega `if (request != null)` |
| No se guarda el usuario | `usuarioId` no existe en la BD | Verifica que el ID del usuario es correcto antes de llamar |
| `@Transactional` no funciona | Service no está anotado con `@Service` | Verifica que la clase tiene `@Service` |
| El timestamp está vacío | Falta `@CreationTimestamp` en la entidad | Agrega la anotación en `AuditLog.timestamp` |
