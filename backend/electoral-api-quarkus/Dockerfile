# Contexto de build: el modulo `backend/electoral-api-quarkus/`.
#
#   docker build -t electoral-api-quarkus backend/electoral-api-quarkus
#
# El changelog de Liquibase vive en src/main/resources/database, dentro del
# modulo. El schema tiene un solo dueno y la imagen no depende de que exista un
# modulo hermano al lado.

# ─── Build ───────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Primero el pom, para que la capa de dependencias se cachee sola.
COPY pom.xml ./pom.xml

# Settings de mirror de Maven Central. Vienen DENTRO del modulo porque el
# contexto de build es el modulo: el `.maven/settings.xml` de la raiz del repo
# no existe para Docker. Ver .mvn/ci/settings-google.xml.
COPY .mvn/ci /tmp/ci-settings

COPY src ./src

# Build. Use system Maven 3.9 from base image (avoid mvnw rate limiting on GitHub Actions).
# -B = batch mode (no interactivity), -DskipTests = skip unit/integration tests in Docker.
# env -i isolates Maven from corrupted environment variables (MAVEN_OPTS, etc).
#
# ARG BUILD_ID para invalidar cache de BuildKit cuando sea necesario.
# Usar en Railway: Variables → BUILD_ID=<unique_timestamp o valor>
# BuildKit incluye valores ARG en el hash de capas, invalidando cache si cambia.
#
# El 429 de Maven Central NO es un fallo del codigo: los runners de GitHub
# Actions comparten IP publica y Central responde "429 Too Many Requests" a ese
# trafico. Reintentar contra el MISMO host no lo arregla (el rate limit sigue
# vivo), asi que cada reintento rota de mirror: Google GCS -> Aliyun ->
# Central. Cada mirror tiene su propio settings porque Maven usa solo el
# PRIMERO que coincide con `mirrorOf` y no hace fallback entre ellos.
ARG BUILD_ID=unknown
RUN echo "Build ID: ${BUILD_ID}" && \
    attempt=1; \
    for mirror in google aliyun central google aliyun; do \
      echo "Maven build attempt $attempt (mirror: $mirror)..."; \
      if env -i PATH=$PATH HOME=$HOME JAVA_HOME=$JAVA_HOME /usr/bin/mvn -B \
           -s /tmp/ci-settings/settings-$mirror.xml \
           -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
           clean package -DskipTests; then \
        exit 0; \
      fi; \
      attempt=$((attempt + 1)); \
      delay=$((30 * attempt)); \
      echo "Attempt with mirror $mirror failed. Retrying in ${delay}s..."; \
      sleep $delay; \
    done; \
    exit 1

# ─── Runtime ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-noble
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/quarkus-app/lib/ ./lib/
COPY --from=builder /app/target/quarkus-app/*.jar ./
COPY --from=builder /app/target/quarkus-app/app/ ./app/
COPY --from=builder /app/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

# Las mismas variables de entorno que el backend Spring: cambiar de implementacion
# no cambia el despliegue.
ENV DB_HOST=postgres \
    DB_PORT=5432 \
    SERVER_PORT=8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
  CMD curl -sf http://localhost:8080/actuator/health || exit 1

CMD ["java", "-jar", "quarkus-run.jar"]
