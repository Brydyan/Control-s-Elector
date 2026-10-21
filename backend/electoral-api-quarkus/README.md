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

El changelog de Liquibase **no está duplicado**: el `pom.xml` lo copia desde
`../electoral-api/src/main/resources/database` en la fase `generate-resources`. El schema tiene un
único dueño y las dos implementaciones no pueden divergir.

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-resources-plugin</artifactId>
  <!-- copia el changelog al classpath -->
</plugin>
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

## Lo que falta

- `refresh`, `logout` y `register` del módulo de auth (incremento 1.1)
- El resto de módulos del API: `observer`, `coordinator`, `evidencias`, `configuracion`,
  `dashboard`, `menus`, `admin`
- El `DataSeeder` (el usuario MASTER inicial), que hoy vive en el backend Spring
- S3 (`quarkus-amazon-s3`) y la evidencia fotográfica
- El mecanismo de autorización por permiso: aca es un filtro propio que replica el
  `JwtAuthFilter` de Spring. Cuando el módulo esté completo conviene adoptar Quarkus Security
  (`quarkus-smallrye-jwt` + `SecurityIdentityAugmentor` para mapear el claim `perms` a roles),
  que es la forma idiomática y la que va a survive mejor al resto de la migración.
