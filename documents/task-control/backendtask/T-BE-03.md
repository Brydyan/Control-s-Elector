# T-BE-03 — Entidades JPA: Actas

**Dificultad:** Intermedio
**Tiempo estimado:** 3-4 horas
**Depende de:** T-BE-02

---

## ¿Qué vas a construir?

Las entidades que representan el corazón del sistema: las **actas electorales**. Una acta registra los votos de una JRV para un cargo específico. También crearás el registro de auditoría (AuditLog) y las notificaciones del sistema.

**Regla importante:** Las actas NUNCA se eliminan. Cuando se "anulan", solo cambia su estado a `ANULADA`. Los datos quedan intactos.

---

## Conceptos clave

### Enum en Java
Un `enum` es una lista de valores fijos posibles. Para el estado del acta:
```
BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA
```
El acta solo puede estar en uno de esos estados. `ANULADA` es el estado final — no hay vuelta atrás.

### @Enumerated(EnumType.STRING)
Le dice a Hibernate que guarde el enum como texto en la BD (ej: "ENVIADA"), no como número. Siempre usa `STRING` — los números son frágiles si reordenas el enum.

### @Column(columnDefinition = "TEXT")
Para campos de texto largo sin límite fijo (como detalles de auditoría).

### @CreationTimestamp
Hibernate llena automáticamente este campo con la fecha/hora actual al crear el registro.

---

## Archivos a crear

```
src/main/java/ec/twintailcs/electoral/domain/
  EstadoActa.java          ← enum con los estados
  Acta.java
  ActaDetalle.java
  AuditLog.java
  Notificacion.java
```

---

## Pasos

### Paso 1 — Enum EstadoActa

```java
// src/main/java/ec/twintailcs/electoral/domain/EstadoActa.java
package ec.twintailcs.electoral.domain;

public enum EstadoActa {
    BORRADOR,    // guardado pero no enviado
    ENVIADA,     // enviado para revisión
    EN_DISPUTA,  // marcado con problema por COORDINATOR
    CORREGIDA,   // corregida por OBSERVER o COORDINATOR
    APROBADA,    // aprobada por COORDINATOR
    ANULADA      // anulada por MASTER — estado final, irreversible
}
```

### Paso 2 — Entidad Acta

El acta relaciona una JRV + un cargo + quién la registró. Solo puede existir UNA acta activa por JRV+cargo (la unicidad se maneja por locking en el servicio).

```java
// src/main/java/ec/twintailcs/electoral/domain/Acta.java
package ec.twintailcs.electoral.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "acta")
public class Acta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // La JRV donde se tomó el acta
    @ManyToOne(optional = false)
    @JoinColumn(name = "jrv_id")
    private Jrv jrv;

    // El cargo que se está contando (Alcalde, Prefecto, etc.)
    @ManyToOne(optional = false)
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    // Quién registró el acta (para RLS: observer ve solo sus actas)
    @ManyToOne(optional = false)
    @JoinColumn(name = "registrada_por")
    private Usuario registrada_por;

    // Estado actual del acta (ver flujo en EstadoActa.java)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoActa estado = EstadoActa.BORRADOR;

    // URL de la foto del acta física en Supabase Storage
    @Column(name = "storage_url")
    private String storageUrl;

    // Datos de sufragantes
    @Column(name = "total_sufragantes", nullable = false)
    private Integer totalSufragantes;

    @Column(name = "votos_blancos", nullable = false)
    private Integer votosBlancos;

    @Column(name = "votos_nulos", nullable = false)
    private Integer votosNulos;

    // Se llena automáticamente al crear
    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false)
    private LocalDateTime fechaRegistro;

    // Motivo de anulación (solo lo llena MASTER)
    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;

    // --- Getters y Setters ---
    // (genera con tu IDE: click derecho → Generate → Getters and Setters)
}
```

### Paso 3 — Entidad ActaDetalle

Cada candidato tiene su propio registro de votos. Así sabemos cuántos votos obtuvo cada candidato en esa acta.

```java
// src/main/java/ec/twintailcs/electoral/domain/ActaDetalle.java
package ec.twintailcs.electoral.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "acta_detalle")
public class ActaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A qué acta pertenece este detalle
    @ManyToOne(optional = false)
    @JoinColumn(name = "acta_id")
    private Acta acta;

    // A qué candidato corresponden estos votos
    @ManyToOne(optional = false)
    @JoinColumn(name = "candidato_id")
    private Candidato candidato;

    // Votos obtenidos por este candidato en esta acta
    @Column(nullable = false)
    private Integer votos;

    // --- Getters y Setters ---
}
```

### Paso 4 — Entidad AuditLog

Registra TODA acción importante del sistema: quién hizo qué, cuándo y desde qué IP. **Nunca se modifica — solo se inserta.**

```java
// src/main/java/ec/twintailcs/electoral/domain/AuditLog.java
package ec.twintailcs.electoral.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Quién hizo la acción (puede ser null si el sistema lo genera)
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Qué hizo: "REGISTRO_ACTA", "ANULACION_ACTA", "LOGIN", etc.
    @Column(nullable = false)
    private String accion;

    // En qué tabla: "acta", "usuario", "partido", etc.
    private String entidad;

    // ID del registro afectado
    @Column(name = "entidad_id")
    private Long entidadId;

    // Descripción detallada de lo que pasó
    @Column(columnDefinition = "TEXT")
    private String detalle;

    // IP desde donde se hizo la acción
    @Column(name = "ip_address")
    private String ipAddress;

    // Se llena automáticamente
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;

    // --- Getters y Setters ---
}
```

### Paso 5 — Entidad Notificacion

Cuando el estado de un acta cambia, el sistema crea una notificación para el destinatario correspondiente.

```java
// src/main/java/ec/twintailcs/electoral/domain/Notificacion.java
package ec.twintailcs.electoral.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // A quién va dirigida la notificación
    @ManyToOne(optional = false)
    @JoinColumn(name = "destinatario_id")
    private Usuario destinatario;

    // Tipo de notificación: "ACTA_EN_DISPUTA", "ACTA_APROBADA", "ACTA_ANULADA"
    @Column(nullable = false)
    private String tipo;

    // El acta relacionada (puede ser null para notificaciones del sistema)
    @ManyToOne
    @JoinColumn(name = "acta_id")
    private Acta acta;

    // Texto del mensaje
    @Column(nullable = false)
    private String mensaje;

    // false = no leída, true = ya la vio
    @Column(nullable = false)
    private Boolean leida = false;

    @CreationTimestamp
    @Column(name = "creada_en", updatable = false)
    private LocalDateTime creadaEn;

    // --- Getters y Setters ---
}
```

---

## Sugerencia de código completa

Ya está en los pasos. Asegúrate de generar los getters/setters con tu IDE (IntelliJ: Alt+Insert → Getter and Setter).

---

## Cómo verificar que funciona

1. Corre la app: `./mvnw spring-boot:run`
2. Hibernate debería crear las 4 tablas automáticamente (ddl-auto=update)
3. Verifica en tu cliente PostgreSQL (DBeaver, pgAdmin, o terminal):
```sql
\dt  -- lista todas las tablas
```
Deberías ver: `acta`, `acta_detalle`, `audit_log`, `notificacion`

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `Unknown column 'cargo_id'` | Falta el `@JoinColumn` | Verifica que el campo tiene `@ManyToOne` + `@JoinColumn` |
| `Column 'estado' cannot be null` | No pusiste valor por defecto | Agrega `= EstadoActa.BORRADOR` al declarar el campo |
| `EnumType.ORDINAL` en logs | Te falta `@Enumerated(EnumType.STRING)` | Agrega la anotación antes del campo `estado` |
| Tabla no se crea | Clase no tiene `@Entity` | Verifica que la anotación está presente |
