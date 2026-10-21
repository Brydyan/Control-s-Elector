# Contexto de build: el directorio `backend/` (no el modulo), porque el changelog de
# Liquibase vive en el modulo hermano y el pom lo copia desde ahi.
#
#   docker build -f backend/electoral-api-quarkus/Dockerfile -t electoral-api-quarkus ./backend
#
# El contexto wider es lo que hace que el schema tenga un solo dueno: no hay una
# segunda copia del changelog que pueda divergir.

# ─── Build ───────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Primero el pom, para que la capa de dependencias se cachee sola.
COPY electoral-api-quarkus/pom.xml ./pom.xml
# El changelog del backend Spring: el schema es compartido.
COPY electoral-api/src/main/resources/database /electoral-api/src/main/resources/database
COPY electoral-api-quarkus/src ./src

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
