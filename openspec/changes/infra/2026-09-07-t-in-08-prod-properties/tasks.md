# Tasks — T-IN-08: application-prod.properties

## Fase A: Crear archivo application-prod.properties

- [ ] **A.1** — Crear archivo:
  `backend/electoral-api/src/main/resources/application-prod.properties`

- [ ] **A.2** — Contenido básico:
  ```properties
  # Logging
  logging.level.root=WARN
  logging.level.org.springframework=INFO
  logging.level.ec.twintailcs=INFO
  
  # JPA
  spring.jpa.hibernate.ddl-auto=validate
  
  # CORS
  spring.web.cors.allowed-origins=https://twintailcs.xyz,https://control.twintailcs.xyz,https://www.twintailcs.xyz
  spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
  spring.web.cors.allowed-headers=Content-Type,Authorization
  spring.web.cors.allow-credentials=true
  
  # Server
  server.port=${SERVER_PORT:8081}
  server.servlet.context-path=/api
  
  # Actuator
  management.endpoints.web.exposure.include=health,info
  management.endpoint.health.show-details=when-authorized
  ```

**Compuerta A:** Archivo existe y es sintácticamente válido.

## Fase B: Test dev vs prod

- [ ] **B.1** — Ejecutar localmente (dev, profile default):
  ```bash
  cd backend/electoral-api
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
  ```
  Verificar: logs muestran DEBUG/INFO detallados.

- [ ] **B.2** — Ejecutar prod (profile prod):
  ```bash
  ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"
  ```
  Verificar: logs muestran solo WARN/ERROR (menos ruido).

**Compuerta B:** Dev = verbose, Prod = silent.

## Fase C: Configurar Railway profile

- [ ] **C.1** — En Railway project → Settings → Environment variables
- [ ] **C.2** — Agregar: `SPRING_PROFILES_ACTIVE=prod`
- [ ] **C.3** — Redeploy en Railway

**Compuerta C:** Railway deploy con prod profile activado.

## Fase D: Verificación en prod

- [ ] **D.1** — En Railway logs, verificar:
  - Nivel de logs WARN/INFO (no DEBUG)
  - "ddl-auto set to validate" en logs JPA
  - Aplicación inicia sin errores

- [ ] **D.2** — Test CORS:
  ```bash
  curl -i -X OPTIONS https://api.twintailcs.xyz/api/auth/login \
    -H "Origin: https://control.twintailcs.xyz" \
    -H "Access-Control-Request-Method: POST"
  ```
  Debe incluir `Access-Control-Allow-Origin: https://control.twintailcs.xyz`.

- [ ] **D.3** — Test HTTPS redirect (si lo agregaste):
  ```bash
  curl -i http://api.twintailcs.xyz/api/auth/login
  ```
  Debe retornar 301/302 redirect a HTTPS.

**Compuerta D:** Logs silenciosos, CORS funciona, HTTPS enforced.

## Qué NO hacer

- **No usar ddl-auto=update** — validar, no modificar
- **No permitir wildcard CORS** — explícito es mejor
- **No loguear passwords** — verificar sensitive data no en logs

## Compuertas

| Fase | Verifica | Expected |
|------|----------|----------|
| A | Archivo existe | Sintaxis válida .properties |
| B | Local dev vs prod | Logs dev=verbose, prod=quiet |
| C | Railway env var | SPRING_PROFILES_ACTIVE=prod |
| D | CORS test | Access-Control-Allow-Origin header |

## Verificación por Mutación

- **V.1** — Cambiar ddl-auto a update → debe pasar pero es riesgo (detecta cambio de estrategia)
- **V.2** — Cambiar CORS a wildcard → debe pasar (pero es inseguro, detecta problema)
