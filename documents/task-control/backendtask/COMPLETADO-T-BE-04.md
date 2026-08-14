# T-BE-04 — Repositorios JPA

**Dificultad:** Principiante
**Tiempo estimado:** 1-2 horas
**Depende de:** T-BE-03

---

## ¿Qué vas a construir?

Los **repositorios** son la capa que habla con la base de datos. En Spring Data JPA, no necesitas escribir SQL para operaciones básicas — solo declaras una interfaz y Spring genera el código automáticamente.

Piénsalo así: tú defines *qué* quieres buscar, Spring se encarga del *cómo*.

---

## Conceptos clave

### JpaRepository<T, ID>
Es una interfaz de Spring que ya tiene métodos listos: `save()`, `findById()`, `findAll()`, `delete()`, etc. Solo necesitas extenderla.

```java
// T = la entidad (Acta, Usuario, etc.)
// ID = el tipo del campo @Id (Long en nuestro caso)
public interface ActaRepository extends JpaRepository<Acta, Long> {
    // Spring ya te da: save(), findById(), findAll(), deleteById()...
}
```

### Métodos por nombre (Query derivation)
Spring lee el nombre del método y genera el SQL automáticamente:
- `findByJrv(Jrv jrv)` → `SELECT * FROM acta WHERE jrv_id = ?`
- `findByObserverAndEstado(Usuario obs, EstadoActa estado)` → `SELECT * FROM acta WHERE observer_id = ? AND estado = ?`
- `countByEstado(EstadoActa estado)` → `SELECT COUNT(*) FROM acta WHERE estado = ?`

### @Query
Para queries más complejas, puedes escribirlas manualmente con JPQL (parecido a SQL pero usa nombres de clases Java, no tablas):

```java
@Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
Optional<Acta> findActaActiva(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);
```

### @Lock(LockModeType.PESSIMISTIC_WRITE)
Bloquea la fila en la BD para que nadie más pueda modificarla hasta que termines tu transacción. Se usa para evitar que dos observers registren la misma JRV+cargo al mismo tiempo.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/repository/
  UsuarioRepository.java
  JrvRepository.java
  ActaRepository.java
  ActaDetalleRepository.java
  AuditLogRepository.java
  NotificacionRepository.java
  RefreshTokenRepository.java
```

---

## Pasos

### Paso 1 — UsuarioRepository

```java
// src/main/java/ec/twintailcs/electoral/repository/UsuarioRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.RolUsuario;
import ec.twintailcs.electoral.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // Buscar por email para el login
    Optional<Usuario> findByEmail(String email);

    // Verificar si ya existe ese email (para registro)
    boolean existsByEmail(String email);

    // Listar todos los usuarios de un rol específico
    List<Usuario> findByRol(RolUsuario rol);
}
```

### Paso 2 — JrvRepository

```java
// src/main/java/ec/twintailcs/electoral/repository/JrvRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.Jrv;
import ec.twintailcs.electoral.domain.Parroquia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JrvRepository extends JpaRepository<Jrv, Long> {

    // JRVs de una parroquia
    List<Jrv> findByParroquia(Parroquia parroquia);
}
```

### Paso 3 — ActaRepository (el más importante)

```java
// src/main/java/ec/twintailcs/electoral/repository/ActaRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActaRepository extends JpaRepository<Acta, Long> {

    // Buscar acta activa (no anulada) por JRV + cargo
    // Se usa para verificar si ya existe antes de registrar una nueva
    @Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
    Optional<Acta> findActaActiva(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);

    // MISMO que el anterior pero con LOCK PESIMISTA
    // Bloquea la fila para evitar dos registros simultáneos
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Acta a WHERE a.jrv = :jrv AND a.cargo = :cargo AND a.estado != 'ANULADA'")
    Optional<Acta> findActaActivaConLock(@Param("jrv") Jrv jrv, @Param("cargo") Cargo cargo);

    // Actas registradas por un usuario específico (RLS filter)
    List<Acta> findByRegistrada_por(Usuario registrada_por);

    // Actas por estado
    List<Acta> findByEstado(EstadoActa estado);

    // Actas registradas por usuario en estado específico
    List<Acta> findByRegistrada_porAndEstado(Usuario registrada_por, EstadoActa estado);

    // Contar actas por estado (para el dashboard)
    long countByEstado(EstadoActa estado);

    // Actas con filtros opcionales para el listado del coordinator
    @Query("""
        SELECT a FROM Acta a
        WHERE (:estado IS NULL OR a.estado = :estado)
          AND (:jrvId IS NULL OR a.jrv.id = :jrvId)
          AND (:cargoId IS NULL OR a.cargo.id = :cargoId)
          AND (:registradaPorId IS NULL OR a.registrada_por.id = :registradaPorId)
        ORDER BY a.registrada_en DESC
        """)
    List<Acta> findWithFilters(
        @Param("estado") EstadoActa estado,
        @Param("jrvId") Long jrvId,
        @Param("cargoId") Long cargoId,
        @Param("registradaPorId") Long registradaPorId
    );
}
```

### Paso 4 — ActaDetalleRepository

```java
// src/main/java/ec/twintailcs/electoral/repository/ActaDetalleRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.Acta;
import ec.twintailcs.electoral.domain.ActaDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ActaDetalleRepository extends JpaRepository<ActaDetalle, Long> {

    // Todos los detalles de una acta
    List<ActaDetalle> findByActa(Acta acta);

    // Borrar los detalles de un acta (para cuando se corrige)
    void deleteByActa(Acta acta);

    // Suma de votos de una acta (para validación)
    @Query("SELECT COALESCE(SUM(d.votos), 0) FROM ActaDetalle d WHERE d.acta = :acta")
    Integer sumarVotosByActa(@Param("acta") Acta acta);
}
```

### Paso 5 — AuditLogRepository

```java
// src/main/java/ec/twintailcs/electoral/repository/AuditLogRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.AuditLog;
import ec.twintailcs.electoral.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Historial de un usuario específico
    List<AuditLog> findByUsuarioOrderByTimestampDesc(Usuario usuario);

    // Historial de una entidad específica (ej: todos los cambios del acta #5)
    List<AuditLog> findByEntidadAndEntidadIdOrderByTimestampDesc(String entidad, Long entidadId);
}
```

### Paso 6 — NotificacionRepository y RefreshTokenRepository

```java
// src/main/java/ec/twintailcs/electoral/repository/NotificacionRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.Notificacion;
import ec.twintailcs.electoral.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Notificaciones no leídas de un usuario
    List<Notificacion> findByDestinatarioAndLeidaFalseOrderByCreadaEnDesc(Usuario destinatario);

    // Contar no leídas (para el badge de la campana)
    long countByDestinatarioAndLeidaFalse(Usuario destinatario);

    // Marcar todas como leídas
    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.destinatario = :usuario")
    void marcarTodasLeidas(@Param("usuario") Usuario usuario);
}
```

```java
// src/main/java/ec/twintailcs/electoral/repository/RefreshTokenRepository.java
package ec.twintailcs.electoral.repository;

import ec.twintailcs.electoral.domain.RefreshToken;
import ec.twintailcs.electoral.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Buscar por el valor del token (para validar refresh)
    Optional<RefreshToken> findByToken(String token);

    // Revocar todos los tokens de un usuario (solo MASTER puede hacer esto)
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revocado = true WHERE rt.usuario = :usuario")
    void revocarTodosDeUsuario(@Param("usuario") Usuario usuario);
}
```

---

## Cómo verificar que funciona

No se puede verificar solo el repositorio — se verifica cuando se usa en un service. Por ahora:

1. Corre `./mvnw spring-boot:run` — debe compilar sin errores
2. Si hay error de compilación, verifica que los imports de las entidades están correctos

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `No property 'X' found for type 'Acta'` | El nombre del método no coincide con el campo | Verifica que el nombre del campo en la entidad es exactamente igual |
| `@Modifying requires @Transactional` | Falta anotación en el service | Agrega `@Transactional` en el método del service que llama este repo |
| `Could not find class 'Jrv'` | Import faltante | Agrega `import ec.twintailcs.electoral.domain.Jrv;` |
| Lock no funciona | Falta `@Transactional` en el service | El lock solo funciona dentro de una transacción activa |
