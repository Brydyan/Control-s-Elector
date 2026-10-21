# electoral-api-quarkus

Backend electoral sobre **Quarkus** — fase 1 de la issue [#113](https://github.com/Brydyan/Control-s-Elector/issues/113) (migración desde Spring Boot).

## Qué es y qué no es

Es el **backend destino del strangler**: comparte schema, contratos HTTP y variables de entorno con
el backend Spring (`backend/electoral-api`). La suite de regresión por contrato
(`backend/contract-tests`) es la que verifica que las dos implementaciones se comportan igual.

**Estado actual: incremento 1 — módulo de autenticación parcial.**

Implementado:

| Endpoint | Estado |
|---|---|
| `POST /api/v1/auth/login` | ✅ completo, mismo body y misma cookie que Spring |
| `GET /api/v1/auth/me` | ✅ completo |
| `GET /actuator/health` | ✅ mismo path, mismo puerto y misma forma de body que Spring |
| `POST /api/v1/auth/refresh` | ⏳ pendiente (increments 1.1) |
| `POST /api/v1/auth/logout` | ⏳ pendiente (increments 1.1) |
| `POST /api/v1/auth/register` | ⏳ pendiente (increments 1.1) |

Los endpoints que faltan devuelven **404** con el shape estándar de error. Eso es lo correcto: la
suite de contrato marca en rojo lo que todavía no está migrado, que es exactamente para lo que
sirve.

## Decisiones de diseño

### Mismo paquete Java que el backend Spring

Las clases usan `ec.twintailcs.electoral.*`, el mismo FQCN que el backend Spring. Mover un módulo
del strangler es **copiar el archivo y cambiar los imports**, no reescribirlo.

### Mismo schema, un solo dueño

El changelog de Liquibase **no está duplicado**: vive en
`src/main/resources/database`, dentro de este módulo. Cuando había dos implementaciones, el
`pom.xml` lo copiaba desde el módulo Spring para que el schema no pudiera divergir; con una sola
implementación, la copia desapareció y el dueño es este módulo.

```
backend/electoral-api-quarkus/src/main/resources/database/
├── db.changelog-master.yaml
└── changelog/*.sql
```

### Nombres de columna explícitos

Spring mapea `publicId` → `public_id` con `CamelCaseToUnderscoresNamingStrategy`. Quarkus **no
aplica ninguna conversión por defecto** y su versión 3.40 no expone la estrategia de naming como
propiedad de configuración. Confiar en la convención implícita hacía que Hibernate buscara una
columna `publicId` donde el schema tiene `public_id`, y el arranque fallaba con
`Schema validation: missing column`.

Por eso **cada columna se declara explícitamente** en la entidad (`@Column(name = "public_id")`).
El mapeo no depende de ninguna convención.

### Mismo contrato de token

El JWT se firma con **Nimbus JOSE**, igual que en Spring, con los mismos claims (`sub`, `iss`,
`typ`, `email`, `rol`, `perms`, `sid`, `iat`, `exp`, `jti`). Los tokens emitidos por una
implementación son aceptados por la otra durante el strangler.

### Mismo shape de error

`shared/web/ErrorMappers` produce `{timestamp, status, error, message}` y, en validaciones de bean,
agrega `fields`. Es el mismo contrato que afirma la suite de regresión.

### Health en el puerto principal

Quarkus sirve el management interface en un puerto **propio** (9000 por defecto) y no monta sus
rutas en el servidor principal aunque se le configure el mismo número de puerto. Como el backend
Spring sirve el health en `/actuator/health` del puerto principal, `shared/web/HealthAliasResource`
lo expone ahí, con la misma forma de body. Así el probe de Kubernetes y el proxy del strangler no
dependen de cuál implementación esté detrás.

### Mismas variables de entorno

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, `APP_JWT_*`,
`AWS_*`. Cambiar de implementación no cambia el despliegue.

## Correrlo

```bash
# Modo JVM
mvn package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar

# Modo nativo (fase 2 de #113)
mvn package -Pnative -DskipTests
./target/*-runner
```

El perfil `native` del `pom.xml` activa `quarkus.native.enabled`, `quarkus.package.type=native` y
`quarkus.native.container-build=true` (el build nativo corre dentro de una imagen de Mandrel, que
necesita más memoria y toolchain de la que tiene un runner normal). Ojo: el perfil se activa con
`-Pnative`; `-Dnative` solo define una variable del sistema y no hace nada.

Con las mismas variables que el backend Spring:

```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=electoral_db \
DB_USER=electoral_user DB_PASSWORD=electoral_pass \
SERVER_PORT=8080 \
APP_JWT_SECRET=... APP_JWT_REFRESH_PEPPER=... \
java -jar target/quarkus-app/quarkus-run.jar
```

## Verificar la paridad

La suite de contrato es black-box: habla solo HTTP contra una URL.

```bash
cd ../contract-tests
mvn -B verify -Dcontract.baseUrl=http://localhost:8080
```

Contra el backend Spring hoy, contra el backend Quarkus mañana. Mismo artefacto de test.

## Mediciones (2026-10-20, misma máquina, misma base)

| Implementación | Arranque hasta health OK | RSS en reposo | Imagen |
|---|---|---|---|
| **Quarkus nativo** | **295 ms** (Quarkus reporta 188 ms) | 136 MB | 120 MB (131 MB de imagen) |
| **Spring Boot (JVM)** | **12,72 s** | 142 MiB | ~200 MB (JRE + jar) |

**67× más rápido en arrancar.** Ese es el número que motivó la migración (#113).

El RSS todavía no es una diferencia: el módulo de Quarkus es una rebanada fina (solo auth) y el
contenedor de Spring todavía no ha invertido su heap. La diferencia de memoria aparece cuando la
aplicación crece y el JVM tiene que expandir el heap, que es justo lo que el heap fijo de la imagen
nativa evita.

El build nativo tarda **3m09s** y usa el builder de Mandrel en un contenedor: necesita 6–8 GB de
memoria. En una máquina chica, `-Dquarkus.native.native-image-xmx=4g` lo baja.

### Trampa del native image: DTOs

Jackson no encuentra serializador para un `record` en native image si no hay metadatos de reflexión.
Como el resource devuelve `Response` (y no el DTO), Quarkus no puede deducir el tipo que tiene que
registrar, y el login responde **500** con `No serializer found for class AuthResponse`.

La respuesta de la propia app lo dice: `Consider annotating the class with '@RegisterForReflection'`.
Por eso `AuthResponse`, `AuthResponse.UsuarioActual` y `LoginRequest` llevan la anotación. **Todo
DTO que cruce el cable en nativo la necesita.**

## Estado del build nativo

| | Estado |
|---|---|
| Slice de autenticación | ✅ compila nativo, 295 ms de arranque, 136 MB RSS |
| Backend completo | ❌ bloqueado por el OpenPDF (ver abajo) |

El bloqueo: `com.lowagie.text.pdf.FopGlyphProcessor` (de OpenPDF) referencia
`org.apache.fop.complexscripts.util.GlyphSequence`, de la dependencia **opcional** de FOP. Sin FOP en el
classpath el build falla con *"Discovered unresolved type during parsing"*. Con FOP aparece la cadena de
`commons-logging` (que tampoco cierra). El detalle y las tres salidas posibles están en la
[issue #114](https://github.com/Brydyan/Control-s-Elector/issues/114).

Dos `--initialize-at-run-time` **sí** hacen falta y ya están en `application.properties` (sin ellos el
build falla con *"Detected an instance of Random/SplittableRandom class in the image heap"*):
`org.apache.http.impl.auth.NTLMEngineImpl` (el cliente HTTP de Apache del AWS SDK v2 crea un
`SecureRandom` en el inicializador de clase) y
`ec.twintailcs.electoral.auth.application.UsuarioAdminService` (tiene un `SecureRandom` estático para
la password temporal).

## Lo que falta

- El build nativo del backend completo (issue #114): el OpenPDF referencia FOP.
- El mecanismo de autorización por permiso: aca es un filtro propio que replicaba el
  `JwtAuthFilter` de Spring. Cuando el módulo esté completo conviene adoptar Quarkus Security
  (`quarkus-smallrye-jwt` + `SecurityIdentityAugmentor` para mapear el claim `perms` a roles),
  que es la forma idiomática y la que va a survive mejor al resto de la migración.

> **Nota de estado**: este módulo ya no tiene módulos pendientes de migrar — los 13 recursos del
> backend están portados y la suite de contrato da verde contra esta implementación. La sección
> ↑
>	listaba los módulos que faltaban durante el strangler; quedó como registro de esa etapa.
