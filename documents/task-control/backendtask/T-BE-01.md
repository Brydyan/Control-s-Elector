# T-BE-01 — Entidades JPA: base geográfica y usuarios

**Dificultad:** Principiante
**Tiempo estimado:** 3 horas
**Depende de:** ninguna

---

## ¿Qué vas a construir?

Vas a crear las clases Java que representan las tablas principales de la base de datos: la geografía electoral (provincia, cantón, parroquia, JRV) y los usuarios del sistema. Estas clases son el corazón del sistema — todo lo demás las usa.

Una **JRV** (Junta Receptora del Voto) es el lugar físico donde se vota. Cada JRV pertenece a una parroquia, cada parroquia a un cantón, y cada cantón a una provincia.

---

## Conceptos clave (léelos antes de empezar)

**¿Qué es una entidad JPA?**
JPA (Java Persistence API) es la forma estándar de Java para trabajar con bases de datos. Una entidad JPA es una clase Java que representa una tabla en la base de datos. Cada instancia de esa clase = una fila en la tabla.

**¿Qué es `@Entity`?**
Le dice a Spring que esta clase representa una tabla en la BD. Sin esto, Spring ignora la clase.

**¿Qué es `@Table`?**
Permite especificar el nombre exacto de la tabla en la BD. Si no lo pones, Spring usa el nombre de la clase en minúsculas.

**¿Qué es `@Id`?**
Marca el campo que es la clave primaria (Primary Key) de la tabla.

**¿Qué es `@GeneratedValue`?**
Le dice a la BD que genere el ID automáticamente (auto-increment). Con `GenerationType.IDENTITY` usa el SERIAL de PostgreSQL.

**¿Qué es `@ManyToOne`?**
Relación de muchos a uno. Ejemplo: muchas JRVs pertenecen a una sola parroquia. En el objeto Java, es simplemente un campo que referencia al otro objeto.

**¿Qué es `@OneToMany`?**
Lo contrario: una parroquia tiene muchas JRVs. Generalmente se pone con `mappedBy` para indicar cuál es el lado "dueño" de la relación.

**¿Qué es un enum en Java?**
Un tipo especial que solo puede tener ciertos valores fijos. Perfecto para roles o estados donde los valores no cambian. En la BD se guarda como texto (String).

---

## Archivos a crear/modificar

```
backend/electoral-api/src/main/java/ec/twintailcs/electoral_api/domain/
  Provincia.java
  Canton.java
  Parroquia.java
  Jrv.java
  RolUsuario.java        ← enum
  Usuario.java
  RefreshToken.java
```

---

## Pasos

### Paso 1 — Crear el enum RolUsuario

Un enum es la forma más segura de manejar valores fijos. Empieza por aquí porque las demás clases lo necesitan.

```java
// Archivo: RolUsuario.java
package ec.twintailcs.electoral_api.domain;

// Un enum define un conjunto cerrado de valores posibles.
// En la BD se guardará como texto: "MASTER", "COORDINATOR", etc.
public enum RolUsuario {
    MASTER,       // Administrador total del sistema
    COORDINATOR,  // Coordinador electoral, gestiona disputas y correcciones
    ADMIN,        // Solo lectura, ve dashboards y estadísticas
    OBSERVER      // Registra actas en cualquier JRV (RLS: ve solo sus registros)
}
```

### Paso 2 — Crear Provincia (la entidad más simple)

```java
// Archivo: Provincia.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

// @Entity = esta clase es una tabla en la BD
@Entity
// @Table = nombre exacto de la tabla en PostgreSQL
@Table(name = "provincia")
public class Provincia {

    // @Id = clave primaria
    // @GeneratedValue = PostgreSQL genera el número automáticamente
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column(nullable = false) = la columna no puede ser NULL en la BD
    @Column(nullable = false, length = 100)
    private String nombre;

    // @Column(unique = true) = no puede haber dos provincias con el mismo código
    @Column(unique = true, length = 10)
    private String codigo;

    // --- Constructores ---
    // JPA necesita un constructor sin argumentos. Siempre ponlo.
    public Provincia() {}

    public Provincia(String nombre, String codigo) {
        this.nombre = nombre;
        this.codigo = codigo;
    }

    // --- Getters y Setters ---
    // JPA los usa para leer y escribir los campos
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
```

### Paso 3 — Crear Canton con relación ManyToOne

```java
// Archivo: Canton.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "canton")
public class Canton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(unique = true, length = 10)
    private String codigo;

    // Relación: muchos cantones pertenecen a una provincia
    // @ManyToOne = del lado "muchos"
    // @JoinColumn = nombre de la columna FK en la tabla canton
    // fetch = LAZY = no cargar la provincia automáticamente (mejor rendimiento)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provincia_id", nullable = false)
    private Provincia provincia;

    public Canton() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Provincia getProvincia() { return provincia; }
    public void setProvincia(Provincia provincia) { this.provincia = provincia; }
}
```

### Paso 4 — Crear Parroquia

```java
// Archivo: Parroquia.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parroquia")
public class Parroquia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    // true = zona urbana, false = zona rural
    // Importante para determinar qué cargos aplican
    @Column(nullable = false)
    private boolean esUrbana;

    // Una parroquia pertenece a un cantón
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canton_id", nullable = false)
    private Canton canton;

    public Parroquia() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isEsUrbana() { return esUrbana; }
    public void setEsUrbana(boolean esUrbana) { this.esUrbana = esUrbana; }

    public Canton getCanton() { return canton; }
    public void setCanton(Canton canton) { this.canton = canton; }
}
```

### Paso 5 — Crear Jrv

```java
// Archivo: Jrv.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "jrv")
public class Jrv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número oficial de la JRV (ej: "001", "045")
    @Column(nullable = false, length = 20)
    private String numero;

    // Nombre del recinto donde está la JRV
    @Column(nullable = false, length = 200)
    private String recinto;

    // Total de electores habilitados en esta JRV
    @Column(nullable = false)
    private Integer totalElectores;

    // Una JRV pertenece a una parroquia
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parroquia_id", nullable = false)
    private Parroquia parroquia;

    public Jrv() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getRecinto() { return recinto; }
    public void setRecinto(String recinto) { this.recinto = recinto; }

    public Integer getTotalElectores() { return totalElectores; }
    public void setTotalElectores(Integer totalElectores) { this.totalElectores = totalElectores; }

    public Parroquia getParroquia() { return parroquia; }
    public void setParroquia(Parroquia parroquia) { this.parroquia = parroquia; }
}
```

### Paso 6 — Crear Usuario

```java
// Archivo: Usuario.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    // El email es el username para login. Debe ser único.
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    // NUNCA guardes la contraseña en texto plano.
    // passwordHash = contraseña cifrada con BCrypt
    @Column(nullable = false)
    private String passwordHash;

    // El rol define qué puede hacer este usuario.
    // @Enumerated(STRING) = guarda "OBSERVER", "MASTER", etc. en la BD (legible)
    // Si usas ORDINAL guarda 0,1,2... (peligroso si cambias el orden del enum)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolUsuario rol;

    // Si activo=false el usuario no puede hacer login
    @Column(nullable = false)
    private boolean activo = true;

    // Fecha de creación del usuario
    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    // @PrePersist = este método se ejecuta automáticamente antes de guardar por primera vez
    @PrePersist
    public void antesDeGuardar() {
        this.creadoEn = LocalDateTime.now();
    }

    public Usuario() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public RolUsuario getRol() { return rol; }
    public void setRol(RolUsuario rol) { this.rol = rol; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
}
```

### Paso 7 — Crear RefreshToken

```java
// Archivo: RefreshToken.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El token es un UUID aleatorio (no un JWT).
    // Se guarda en BD para poder revocarlo.
    @Column(nullable = false, unique = true)
    private String token;

    // A qué usuario pertenece este token
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    // Cuándo expira este token (8 horas desde creación)
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // Si revocado=true, el token no es válido aunque no haya expirado.
    // Permite logout forzado (solo MASTER puede hacerlo para otros usuarios).
    @Column(nullable = false)
    private boolean revocado = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    public void antesDeGuardar() {
        this.creadoEn = LocalDateTime.now();
    }

    public RefreshToken() {}

    // Método útil: ¿este token sigue siendo válido?
    public boolean esValido() {
        return !revocado && LocalDateTime.now().isBefore(expiresAt);
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRevocado() { return revocado; }
    public void setRevocado(boolean revocado) { this.revocado = revocado; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
}
```

---

## Sugerencia de código completa

El código de cada clase está completo en los pasos anteriores. Asegúrate de que tu `application.properties` tenga:

```properties
# Esto le dice a Spring que cree/actualice las tablas automáticamente
# En producción usa "validate" o "none"
spring.jpa.hibernate.ddl-auto=update

# Ver el SQL que genera Hibernate (útil para aprender)
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

---

## Cómo verificar que funciona

1. Arranca la aplicación con `./mvnw spring-boot:run`
2. En los logs deberías ver Hibernate generando las tablas:
   ```
   Hibernate: create table provincia (id bigserial not null, codigo varchar(10), nombre varchar(100) not null, primary key (id))
   ```
3. Conéctate a PostgreSQL y verifica que las tablas existen:
   ```sql
   \dt   -- lista todas las tablas
   \d usuario  -- describe la estructura de la tabla usuario
   ```
4. Tip: Si usas IntelliJ, instala el plugin "Database Tools" para ver las tablas visualmente.

---

## Errores comunes

**Error: `No default constructor for entity`**
Solución: JPA exige un constructor sin argumentos en cada entidad. Agrega `public NombreClase() {}`.

**Error: `could not find a column named X`**
Solución: Revisa que el nombre del campo en Java coincida con lo que Hibernate intentó crear. Con `show-sql=true` puedes ver exactamente el SQL que intenta ejecutar.

**Error: `org.hibernate.MappingException: Could not determine type for: RolUsuario`**
Solución: Falta `@Enumerated(EnumType.STRING)` en el campo `rol` de Usuario.

**Error: tablas se crean pero los datos se pierden al reiniciar**
Razón: con `ddl-auto=create` Hibernate borra y recrea las tablas cada vez. Usa `update` para desarrollo.

**Los IDs empiezan en 1 pero en PostgreSQL muestran números grandes**
Razón: con `GenerationType.IDENTITY` PostgreSQL usa una secuencia que puede saltar números. Es normal.

**Busca en Google:** "Spring Boot JPA Entity tutorial", "Hibernate @ManyToOne example"
