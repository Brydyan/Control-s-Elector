# Convenciones de Código — electoral-api (Java 25 / Spring Boot)

> Este documento define las convenciones de código para el backend de
> Control Electoral. Es vinculante para todo el código nuevo y refactors.
>
> Java 25 es LTS (septiembre 2025). Usamos sus features finalizados,
> no preview/incubator, salvo decisión explícita del equipo.

---

## 1. Naming Conventions

### 1.1 Regla general: CamelCase estricto

| Tipo | Case | Ejemplo |
|---|---|---|
| Clases / Interfaces / Enums / Records | **UpperCamelCase** | `EvidenciaService`, `LoginDTO`, `EvidenciaRepository` |
| Métodos | **lowerCamelCase** | `calcularTotal()`, `findByEmail()`, `presignDownload()` |
| Variables / Parámetros | **lowerCamelCase** | `totalCount`, `adminEmail`, `jrvId` |
| Constantes (`static final`) | **UPPER_SNAKE_CASE** | `MAX_FILE_SIZE`, `DEFAULT_TIMEOUT` |
| Paquetes | **Lowercase** | `ec.twintailcs.electoral.auth.web` |
| Type parameters (genéricos) | Una letra | `<T>`, `<E>`, `<K, V>` |

### 1.2 Acrónimos en CamelCase

Un acrónimo de 2+ letras se trata como **una palabra**:

| Correcto | Incorrecto |
|---|---|
| `S3StorageService` | `S3StorageService` (ok — S3 es 2 chars) |
| `JwtService` (NO `JWTService`) | ~~`JWTService`~~ |
| `HttpStatus` (NO `HTTPStatus`) | ~~`HTTPStatus`~~ |
| `evidenciaUploadUrl` | ~~`evidenciaUploadURL`~~ |
| `HtmlParser` | ~~`HTMLParser`~~ |

Regla: solo la primera letra del acrónimo va en mayúscula. `JwtService`,
`HttpRequest`, `UrlBuilder`, `SqlQuery`.

### 1.3 DTO naming (CRÍTICO)

Los DTOs **SIEMPRE** llevan sufijo `DTO`, independientemente de su uso:

| Correcto | Incorrecto |
|---|---|
| `LoginDTO` | ~~`LoginRequest`~~ |
| `RegisterDTO` | ~~`RegisterRequest`~~ |
| `CambiarEstadoDTO` | ~~`CambiarEstadoRequest`~~ |
| `EvidenciaResponseDTO` | ~~`EvidenciaResponse`~~ |
| `AuthResponseDTO` | ~~`AuthResponse`~~ |
| `DownloadUrlDTO` | ~~`DownloadUrlResponse`~~ |
| `EvidenciaUploadDTO` | ~~`EvidenciaUploadRequest`~~ |

Excepción: si un archivo existe y NO es DTO (ej. `EvidenciaUploadRequest`),
mantener el nombre. Pero si es DTO → `*DTO.java`.

### 1.4 Repositorios y Servicios

| Tipo | Sufijo | Ejemplo |
|---|---|---|
| Repository (Spring Data) | `Repository` | `UsuarioRepository` |
| Service (Spring `@Service`) | `Service` | `AuthService`, `EvidenciaService` |
| Controller (Spring `@RestController`) | `Controller` | `AuthController`, `EvidenciaController` |
| Config (Spring `@Configuration`) | `Config` | `SecurityConfig`, `S3Config` |
| DTO | `DTO` | `LoginDTO`, `EvidenciaResponseDTO` |
| Mapper | `Mapper` | `EvidenciaMapper` |
| Exception | `Exception` / `NotFoundException` | `EvidenciaNotFoundException` |
| Properties | `Properties` | `S3Properties` |
| Health Indicator | `HealthIndicator` | `S3HealthIndicator` |

---

## 2. Package Structure (Hexagonal / Screaming Architecture)

```
ec.twintailcs.electoral
├── auth/
│   ├── domain/        # Entidades, repositorios (Puertos)
│   ├── application/   # Casos de uso, servicios de aplicación
│   ├── web/           # Controllers, filters, DTOs
│   │   └── dto/       # DTOs exclusivamente
│   └── config/        # Beans, configuración, seeders
├── evidencia/
│   ├── domain/
│   ├── application/
│   ├── web/
│   │   └── dto/
│   └── config/        # (si aplica)
├── shared/
│   └── web/           # Cross-cutting: GlobalExceptionHandler, etc.
└── storage/
    └── s3/            # S3 adapter
```

### 2.1 Reglas de paquetes

- Un módulo (auth, evidencia) tiene **al menos** `domain/`. `application/` y
  `web/` se agregan cuando el módulo los necesita.
- `domain/` **nunca importa** de `application/` o `web/`. Zero dependencia.
- `application/` importa de `domain/`. No importa de `web/`.
- `web/` importa de `domain/` y `application/`.
- `config/` importa libremente (es infra, no negocio).
- Un DTO vive en `web/dto/` (capa de presentación), **no** en `domain/`.

---

## 3. Java 25 — Features Disponibles (LTS)

Features **finalizados** que podemos usar sin flags:

| Feature | JEP | Uso |
|---|---|---|
| Module Import Declarations | 511 | `import module java.base;` — permite importar todo un módulo. Útil en tests o scripts. En código de producción preferir imports explícitos. |
| Flexible Constructor Bodies | 513 | Statements antes de `super()` — permite validar parámetros antes de llamar al constructor padre. |
| Unnamed Variables (`_`) | 456 | `try { ... } catch (_) { }` o `stream.map(_ -> ...)` |
| Scoped Values (final) | 506 | Reemplazo de `ThreadLocal` para shared context. NO usar todavía hasta que el equipo lo adopte. Seguir con `@RequestScope` o parámetros explícitos. |
| Key Derivation Function API | 510 | `KDF.getInstance("HKDF-SHA256")` — para derivación de claves. Usar solo si el feature lo requiere. |
| Instance Main Methods | 512 | `void main() { ... }` sin `public static`. No aplica a Spring Boot. |
| Pattern Matching for `switch` (final desde 21) | 441 | `switch (obj) { case String s -> ... }` |
| Record Patterns (final desde 21) | 440 | `if (obj instanceof Point(int x, int y))` |
| Sequenced Collections (final desde 21) | 431 | `list.getFirst()`, `list.getLast()`, `list.reversed()` |
| Virtual Threads (final desde 21) | 444 | `Thread.ofVirtual().start(...)` — Spring Boot 4.x lo usa por defecto. |

Features **NO usar** (preview / incubator):

| Feature | JEP | Motivo |
|---|---|---|
| Primitive Types in Patterns | 507 | Tercer preview — puede cambiar |
| Structured Concurrency | 505 | Quinto preview — esperar |
| Vector API | 508 | Tenth incubator — solo para cómputo numérico heavy |
| Stable Values | 502 | Preview |
| PEM Encodings | 470 | Preview |

---

## 4. Estilo de Código

### 4.1 Formato

- **Indentación**: 4 espacios. Sin tabs.
- **Braces**: estilo K&R (llave de apertura en la misma línea).
- **Límite de línea**: 120 caracteres (no 80 — es más realista para Java moderno).
- **Imports**: orden alfabético. Sin wildcards (`import java.util.*` no; usar imports específicos).
- **Organización de miembros**: 1. constantes, 2. fields, 3. constructor, 4. métodos públicos, 5. métodos privados.

### 4.2 Records vs Clases

- Usar **records** para DTOs inmutables y value objects simples.
- Usar **clases** cuando necesitamos JPA (`@Entity`), herencia, o lógica compleja.

```java
// Record para DTO inmutable
public record LoginDTO(String email, String password) {}

// Clase para entidad JPA
@Entity
@Table(name = "usuario")
public class Usuario {
    @Id @GeneratedValue(strategy = IDENTITY)
    private Long id;
    // ...
}
```

### 4.3 Optional

- `Optional` solo para **retorno de métodos** que pueden no tener valor.
- NO usar `Optional` como parámetro, field, o en colecciones.
- `orElseThrow()` con mensaje descriptivo:

```java
return repository.findById(id)
    .orElseThrow(() -> new EvidenciaNotFoundException("Evidencia " + id + " no encontrada"));
```

### 4.4 null

- Evitar `null` en lo posible.
- Parámetros: usar `@NonNull` / `@Nullable` de Spring (o `jakarta.annotation`).
- Colecciones: inicializar vacías (`List.of()`, `Collections.emptyList()`).

### 4.5 Testing

- Tests unitarios con JUnit 5 + Mockito.
- Tests de integración con `@SpringBootTest`.
- Nombre de test: `metodo_descripcion()` en snake_case (única excepción al camelCase):

```java
@Test
void login_conCredencialesValidas_retornaTokens() { }
@Test
void login_conEmailInexistente_lanzaException() { }
```

---

## 5. Commits y PRs

- Commits en **Inglés**, formato conventional commits:
  `feat(scope): mensaje` | `fix(scope): mensaje` | `refactor(scope): mensaje`
- PRs pueden ser en **Español** (descripción) con título en Inglés.
- Commits atómicos: un cambio lógico por commit.

---

## 6. Vigencia

Este documento aplica desde julio 2026. Se revisa cuando:

- Se actualiza la versión LTS de Java.
- El equipo acuerda adoptar un feature que hoy está en preview.
- Se identifica una inconsistencia recurrente en el código.

---

## 7. Referencias

- [Oracle Code Conventions (archived)](https://www.oracle.com/java/technologies/javase/codeconventions-namingconventions.html)
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Java 25 Release Notes](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html)
- [Baeldung — Java Naming Conventions](https://www.baeldung.com/java-naming-conventions)
