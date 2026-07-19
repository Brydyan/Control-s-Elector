# T-BE-02 — Entidades JPA: electoral

**Dificultad:** Principiante
**Tiempo estimado:** 2.5 horas
**Depende de:** T-BE-01

---

## ¿Qué vas a construir?

Vas a crear las entidades que representan la estructura electoral: los cargos a elegir (Prefecto, Alcalde, etc.), los partidos políticos, los candidatos, y la tabla que relaciona JRVs con cargos.

Estas entidades definen **qué se vota** en cada JRV según su ubicación geográfica (JRV_CARGO).

---

## Conceptos clave (léelos antes de empezar)

**¿Qué es una tabla junction (tabla de unión)?**
Cuando dos entidades se relacionan de muchos a muchos (una JRV puede tener varios cargos, un cargo puede estar en varias JRVs), se necesita una tabla intermedia. Esta tabla solo contiene los IDs de las dos entidades relacionadas. Se llama "tabla junction" o "tabla de unión".

**¿`@ManyToMany` directo o tabla junction propia?**
Spring permite `@ManyToMany` y crea la tabla junction automáticamente. Pero si la tabla junction necesita campos extras (como `activo`, `fechaAsignacion`), es mejor crear una entidad propia y dos `@ManyToOne`.

Regla simple: si la tabla junction puede necesitar datos extra en el futuro → crea entidad propia. En este proyecto, `JrvCargo` puede necesitar un campo `activo` para deshabilitar un cargo en una JRV específica, así que usamos entidad propia.

**`@EmbeddedId`**
Una clave primaria compuesta usando un objeto embebido. Útil para tablas junction donde la PK es la combinación de dos FKs.

**¿Qué es `NivelCargo`?**
Enum que define el alcance geográfico de un cargo electoral. Prefecto aplica a toda la PROVINCIA. Alcalde aplica al CANTON. Vocal de junta parroquial solo a la PARROQUIA.

---

## Archivos a crear/modificar

```
backend/electoral-api/src/main/java/ec/twintailcs/electoral_api/domain/
  NivelCargo.java              ← enum
  Cargo.java
  Partido.java
  Candidato.java
  JrvCargoId.java              ← clave primaria compuesta
  JrvCargo.java
```

---

## Pasos

### Paso 1 — Crear el enum NivelCargo

```java
// Archivo: NivelCargo.java
package ec.twintailcs.electoral_api.domain;

// Define el alcance geográfico de cada cargo electoral
public enum NivelCargo {
    NACIONAL,   // Consejero CPCCS — aplica en TODAS las JRVs del país
    PROVINCIA,  // Prefecto/a — aplica en todas las JRVs de la provincia
    CANTON,     // Alcalde, Concejal Urbano/Rural — aplica en JRVs del cantón
    PARROQUIA   // Vocal Junta Parroquial — solo en parroquias rurales
}
```

### Paso 2 — Crear Cargo

```java
// Archivo: Cargo.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cargo")
public class Cargo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del cargo. Ej: "Prefecto/a", "Alcalde/sa", "Concejal Urbano"
    @Column(nullable = false, length = 100)
    private String nombre;

    // El nivel determina en qué JRVs aplica este cargo
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelCargo nivel;

    // Número de representantes a elegir para este cargo
    // Ej: hay 5 concejales urbanos por cantón
    @Column(nullable = false)
    private Integer puestos;

    public Cargo() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public NivelCargo getNivel() { return nivel; }
    public void setNivel(NivelCargo nivel) { this.nivel = nivel; }

    public Integer getPuestos() { return puestos; }
    public void setPuestos(Integer puestos) { this.puestos = puestos; }
}
```

### Paso 3 — Crear Partido

```java
// Archivo: Partido.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "partido")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre completo del partido. Ej: "Movimiento Ciudadano"
    @Column(nullable = false, length = 150)
    private String nombre;

    // Siglas o nombre corto. Ej: "MC"
    @Column(length = 20)
    private String siglas;

    // Número de lista asignado por el CNE
    @Column(unique = true)
    private Integer numeroLista;

    public Partido() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }

    public Integer getNumeroLista() { return numeroLista; }
    public void setNumeroLista(Integer numeroLista) { this.numeroLista = numeroLista; }
}
```

### Paso 4 — Crear Candidato

```java
// Archivo: Candidato.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "candidato")
public class Candidato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nombre;

    // Número de orden dentro de la lista del partido para este cargo
    // Ej: el candidato 1 es el principal, 2 es el alterno
    @Column(nullable = false)
    private Integer orden;

    // A qué cargo aplica esta candidatura
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_id", nullable = false)
    private Cargo cargo;

    // De qué partido es este candidato
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    public Candidato() {}

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getOrden() { return orden; }
    public void setOrden(Integer orden) { this.orden = orden; }

    public Cargo getCargo() { return cargo; }
    public void setCargo(Cargo cargo) { this.cargo = cargo; }

    public Partido getPartido() { return partido; }
    public void setPartido(Partido partido) { this.partido = partido; }
}
```

### Paso 5 — Crear JrvCargoId (clave compuesta)

Para la tabla junction necesitamos una PK que sea la combinación de jrv_id + cargo_id. Creamos una clase separada para eso.

```java
// Archivo: JrvCargoId.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

// @Embeddable = esta clase puede "incrustarse" en otra entidad como parte de ella
// Serializable = requerido por JPA para claves compuestas
@Embeddable
public class JrvCargoId implements Serializable {

    private Long jrvId;
    private Long cargoId;

    public JrvCargoId() {}

    public JrvCargoId(Long jrvId, Long cargoId) {
        this.jrvId = jrvId;
        this.cargoId = cargoId;
    }

    // equals y hashCode SON OBLIGATORIOS en claves compuestas.
    // JPA los usa para comparar objetos y buscar en caché.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JrvCargoId)) return false;
        JrvCargoId that = (JrvCargoId) o;
        return Objects.equals(jrvId, that.jrvId) && Objects.equals(cargoId, that.cargoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jrvId, cargoId);
    }

    // Getters y Setters
    public Long getJrvId() { return jrvId; }
    public void setJrvId(Long jrvId) { this.jrvId = jrvId; }

    public Long getCargoId() { return cargoId; }
    public void setCargoId(Long cargoId) { this.cargoId = cargoId; }
}
```

### Paso 6 — Crear JrvCargo (tabla junction con entidad propia)

```java
// Archivo: JrvCargo.java
package ec.twintailcs.electoral_api.domain;

import jakarta.persistence.*;

// Esta tabla dice "la JRV X tiene que registrar el cargo Y"
// Es la que define qué cargos aparecen en cada JRV
@Entity
@Table(name = "jrv_cargo")
public class JrvCargo {

    // @EmbeddedId = la PK es el objeto JrvCargoId que creamos antes
    @EmbeddedId
    private JrvCargoId id;

    // @MapsId("jrvId") = el campo jrvId del @EmbeddedId corresponde a esta FK
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("jrvId")
    @JoinColumn(name = "jrv_id")
    private Jrv jrv;

    // @MapsId("cargoId") = el campo cargoId del @EmbeddedId corresponde a esta FK
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("cargoId")
    @JoinColumn(name = "cargo_id")
    private Cargo cargo;

    // Podemos agregar campos extra a esta tabla junction
    // Por ejemplo, si este cargo está activo en esta JRV específica
    @Column(nullable = false)
    private boolean activo = true;

    public JrvCargo() {}

    public JrvCargo(Jrv jrv, Cargo cargo) {
        this.jrv = jrv;
        this.cargo = cargo;
        // Creamos el ID compuesto a partir de los objetos
        this.id = new JrvCargoId(jrv.getId(), cargo.getId());
    }

    // Getters y Setters
    public JrvCargoId getId() { return id; }
    public void setId(JrvCargoId id) { this.id = id; }

    public Jrv getJrv() { return jrv; }
    public void setJrv(Jrv jrv) { this.jrv = jrv; }

    public Cargo getCargo() { return cargo; }
    public void setCargo(Cargo cargo) { this.cargo = cargo; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
```

---

## Sugerencia de código completa

Todos los archivos están completos en los pasos anteriores. Asegúrate de tener el `application.properties` con `ddl-auto=update` como se mostró en T-BE-01.

---

## Cómo verificar que funciona

1. Arranca la app y verifica en los logs que Hibernate crea las tablas sin errores.
2. Conecta a la BD y ejecuta:
   ```sql
   -- Ver las tablas creadas
   \dt
   -- Ver la estructura de jrv_cargo (debe tener PK compuesta)
   \d jrv_cargo
   -- Debe mostrar: Primary key: (jrv_id, cargo_id)
   ```
3. Inserta datos de prueba manualmente para verificar las FK:
   ```sql
   INSERT INTO cargo (nombre, nivel, puestos) VALUES ('Prefecto/a', 'PROVINCIA', 1);
   INSERT INTO partido (nombre, siglas, numero_lista) VALUES ('Movimiento Electoral', 'ME', 1);
   ```

---

## Errores comunes

**Error: `No serializer found for class... JrvCargoId`**
Solución: Asegúrate de que `JrvCargoId` implementa `Serializable`. Es obligatorio para claves compuestas.

**Error: `@EmbeddedId and @Id are mutually exclusive`**
Solución: Cuando usas `@EmbeddedId` no debes agregar otro `@Id`. La clase embebida ya es la PK.

**Error: `Column jrv_id... not found`**
Solución: Al usar `@MapsId`, Hibernate espera que el campo del `@EmbeddedId` se llame exactamente igual. Verifica que `JrvCargoId` tiene `jrvId` y `cargoId` (camelCase), y que `@MapsId("jrvId")` coincide exactamente.

**Error al crear `JrvCargo` nuevo porque `jrv.getId()` retorna null**
Solución: Si estás creando objetos nuevos que aún no se guardaron en la BD, no tienen ID todavía. Guarda primero la JRV y el Cargo antes de crear el JrvCargo.

**¿Por qué no uso `@ManyToMany` directamente?**
Con `@ManyToMany`, Spring crea la tabla automáticamente pero no puedes agregarle campos. Si en el futuro necesitas saber cuándo se asignó el cargo a la JRV, o si está activo, ya no puedes. Es mejor práctica crear la entidad junction propia desde el principio.

**Busca en Google:** "JPA @EmbeddedId example", "Spring Boot many to many with extra column"
