# Proposal — T-IN-08: application-prod.properties

## Qué se construye
Archivo `application-prod.properties`: configuración Spring Boot específica para producción Railway.

## Por qué
- Desactiva logs verbose (producción no necesita debug)
- Activa validación de schema JPA
- Configura seguridad: HTTPS, CORS prod-only
- Profile-based: `spring.profiles.active=prod` en Railway

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Logging | level WARN, no debug | CloudWatch setup |
| JPA | ddl-auto=validate (no update) | custom entity listeners |
| HTTPS | https-only enforcement | Let's Encrypt auto-renew |
| CORS | allowedOrigins=twintailcs.xyz | wildcard * |
| Security | headers, CSRF protect | WAF rules |

## Criterio de aceptación

- [ ] Archivo creado en `backend/electoral-api/src/main/resources/application-prod.properties`
- [ ] Logging = WARN
- [ ] JPA ddl-auto = validate
- [ ] CORS allowedOrigins = twintailcs.xyz
- [ ] Spring Security enforce HTTPS
- [ ] Railway env var `SPRING_PROFILES_ACTIVE=prod` activada
- [ ] Test: dev build vs prod build, logs diferente verbosity

## Fuera de alcance

- SSL cert management (Railway lo hace)
- Monitoring/alertas
- Rate limiting

## Dependencias

- T-IN-07 (deploy Railway configurado)

## Decisiones a tomar

**D1 — ddl-auto strategy:** validate (no create/update). Schema manejada manualmente (Liquibase).

**D2 — CORS origins:** twintailcs.xyz + control.twintailcs.xyz (frontend).
