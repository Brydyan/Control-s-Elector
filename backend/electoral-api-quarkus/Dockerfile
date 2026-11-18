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

# Copiar Maven Wrapper (igual que el builder nativo).
# El wrapper garantiza que siempre usamos Maven 3.9.16.
COPY mvnw ./
COPY .mvn ./.mvn

# Primero el pom, para que la capa de dependencias se cachee sola.
COPY pom.xml ./pom.xml

# Descargar dependencias con caché montada. BuildKit preserva /root/.m2
# entre builds, así que la segunda ejecución reutiliza artifacts ya descargados.
# Esto evita rate limiting y timeouts en Maven central.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp dependency:go-offline -DskipTests

COPY src ./src

# Build del package con caché (reutiliza las deps descargadas arriba).
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests

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
