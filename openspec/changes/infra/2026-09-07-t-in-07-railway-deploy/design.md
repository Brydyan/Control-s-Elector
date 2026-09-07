# Design — T-IN-07: Railway Deploy

## D1 — Dockerfile

**Decisión:** Si existe en repo, usar. Si no, generar uno simple:
```dockerfile
FROM eclipse-temurin:21-jre
COPY backend/electoral-api/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Alternativa descartada — Buildpack.** Railway auto-detect es OK pero Dockerfile es más control.

## D2 — Environment vars en Railway

**Decisión:** Configurar en Railway Settings:
- SUPABASE_URL
- SUPABASE_SERVICE_ROLE_KEY (secret)
- APP_ENV=prod
- Otros (JWT_SECRET si no está en Supabase, etc.)

**Alternativa descartada — .env file.** Menos seguro, .env nunca debe commitarse.

## D3 — Build command

**Decisión:** Railway auto-detect Maven build. Si necesita override: `mvn clean package -DskipTests`

**Alternativa descartada — Gradle.** Proyecto usa Maven.

## D4 — Port

**Decisión:** Spring Boot expone puerto 8081 (en application-prod.properties). Railway asigna PUBLIC_PORT variable, bind a 8081.

**Alternativa descartada — port 8080.** Nuestro stack usa 8081.
