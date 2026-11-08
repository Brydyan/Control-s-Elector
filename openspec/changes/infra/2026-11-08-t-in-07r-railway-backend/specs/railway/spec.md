# Spec: Railway Backend T-IN-07r

## RF-RW-01: Backend arranca sin DB local

GIVEN backend sin Postgres en localhost
WHEN `./mvnw spring-boot:run -Dspring.profiles.active=prod`
THEN falla rápido con mensaje claro:
"Cannot connect to Neon: SPRING_DATASOURCE_URL not set or unreachable"

## RF-RW-02: Backend arranca en Railway con Neon

GIVEN Railway env vars inyectadas (SPRING_DATASOURCE_URL, AWS_S3_*)
WHEN boot ejecuta
THEN backend arranca en <30s, Liquibase corre una sola vez

## RF-RW-03: Healthchecks operacionales

GIVEN backend corriendo en Railway
WHEN cliente hace `GET /api/v1/health/db`, `/health/storage`, `/health/backend`
THEN todas responden 200 (si Neon/R2 ok) o 503 (si recursos caídos)

## RF-RW-04: Application.properties mantiene secrets

GIVEN application.properties default
WHEN ninguna var de env está seteada (local)
THEN usa valores default (Testcontainers, localhost)
Y en Railway, env vars sobreescriben via Spring externalized config

## RF-RW-05: Profiles no colisionan

GIVEN `application.properties` + `application-prod.properties`
WHEN activás `spring.profiles.active=prod`
THEN properties del prod REMPLAZAN las del default (Spring boot behavior)

## RF-RW-06: Liquibase idempotencia en Railway

GIVEN dos instancias boot simultáneamente
CUANDO Liquibase corre
THEN una ejecuta, la otra espera en lock, sin conflictos

## RNF-RW-01: Boot time aceptable

GIVEN backend en Railway
WHEN `docker run`
ENTONCES startup time ≤ 60s (normal para Quarkus + Liquibase)

## RNF-RW-02: Memory usage aceptable

GIVEN backend Quarkus con modo JVM (no nativo, MVP)
WHEN arranca en Railway
THEN RSS ≤ 512 MB (Quarkus típico ~350 MB + buffer)

## RNF-RW-03: No secrets en logs

GIVEN backend corriendo
WHEN logs se envían a Railway console
THEN no aparecen valores de SPRING_DATASOURCE_URL, AWS_SECRET_ACCESS_KEY, etc.
