# Spec — T-IN-07: Railway Deploy

## Escenarios

### S1 — Deploy inicial

```gherkin
GIVEN    GitHub repo con Spring Boot backend
AND      Dockerfile presente (o auto-generate)
WHEN     conecta repo a Railway project
THEN     Railway detecta Dockerfile/Maven
AND      inicia build automático
AND      deploy completa sin errores
```

### S2 — API accesible en prod

```gherkin
GIVEN    deploy completado
WHEN     hace request a https://api.twintailcs.xyz/swagger-ui.html
THEN     Swagger carga (Spring Boot está respondiendo)
```

### S3 — Environment vars configuradas

```gherkin
GIVEN    Railway Settings → Variables
WHEN     ingresa SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY
THEN     Spring Boot inicia, conecta a Supabase
AND      logs muestran conexión exitosa
```

### S4 — Healthcheck

```gherkin
GIVEN    API en prod
WHEN     hace GET /actuator/health
THEN     retorna {status: 'UP'}
```

## Mutaciones

### M1 — Remover Dockerfile

Si no existe Dockerfile, Railway debe detectar Maven y buildear. Verificación: build exitoso sin Dockerfile.

### M2 — Environment var faltante

Si SUPABASE_URL no está configured, Spring Boot debe fallar al iniciar. Verificación: logs muestran error de conexión.

## Garantías

- **G1:** API accesible en https://api.twintailcs.xyz
- **G2:** Logs visibles en Railway dashboard
- **G3:** Deploy automático en push a main
- **G4:** Healthcheck retorna UP
