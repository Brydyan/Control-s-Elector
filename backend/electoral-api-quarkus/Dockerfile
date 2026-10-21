# Contexto de build: el modulo `backend/electoral-api-quarkus/`.
#
#   docker build -t electoral-api-quarkus backend/electoral-api-quarkus
#
# El changelog de Liquibase vive en src/main/resources/database, dentro del
# modulo. El schema tiene un solo dueno y la imagen no depende de que exista un
# modulo hermano al lado.

# ─── Build ───────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Primero el pom, para que la capa de dependencias se cachee sola.
COPY pom.xml ./pom.xml
COPY src ./src

RUN mvn -B package -DskipTests

# ─── Runtime ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

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
