# Spec — T-IN-08: application-prod.properties

## Escenarios

### S1 — Logging silencioso en prod

```gherkin
GIVEN    Spring Boot en prod (profile=prod)
WHEN     inicia y procesa requests
THEN     logs muestran solo WARN/ERROR
AND      no aparecen DEBUG/INFO verbosos
```

### S2 — JPA valida schema

```gherkin
GIVEN    Liquibase ya creó tablas
AND      profile=prod (ddl-auto=validate)
WHEN     Spring Boot inicia
THEN     JPA verifica que entidades coincidan con BD
AND      si falta columna, lanza error
```

### S3 — CORS restringido

```gherkin
GIVEN    Frontend en control.twintailcs.xyz
WHEN     hace request POST /api/auth/login
THEN     response incluye header Access-Control-Allow-Origin: https://control.twintailcs.xyz
```

### S4 — HTTPS enforced

```gherkin
GIVEN    cliente hace request HTTP (no HTTPS)
WHEN     Spring Security intercepta
THEN     redirige a HTTPS (status 301/302)
```

## Mutaciones

### M1 — ddl-auto=update

Si cambia a update, JPA podría crear columnas no esperadas. Verificación: schema debería validar pero sin problemas (test que no quebró).

### M2 — CORS = wildcard

Si allowedOrigins = *, cualquier frontend puede acceder. Verificación: origen.example.com hace request, debe funcionar (detecta falla de seguridad).

## Garantías

- **G1:** Logging WARN en prod
- **G2:** JPA valida schema
- **G3:** CORS restringido a twintailcs.xyz
- **G4:** HTTPS enforced
