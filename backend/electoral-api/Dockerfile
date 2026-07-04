# =============================================================================
# Dockerfile — Backend Spring Boot
# =============================================================================
# Dev local no usa este Dockerfile: el backend se levanta directo con
# `mvn spring-boot:run` en el host (ver DOCKER.md).
#
# Este Dockerfile produce una imagen lista para deploy en Railway
# (o cualquier plataforma que soporte imágenes Docker estándar).
# El binario que se copia al runtime stage es un JAR ejecutable de Spring Boot
# que corre con la JVM estándar de Eclipse Temurin (no requiere GraalVM ni
# AOT). Esto es lo que se deploya actualmente; si en el futuro se quiere
# pasar a GraalVM Native Image, basta con cambiar el builder stage
# (ver Dockerfile.native).
#
# Resultado: imagen final ~250 MB, no requiere JDK instalado, no requiere
# compilación AOT en build (descartar este archivo si ya no se deploya
# vía Docker o Railway).
#
# Build:    docker build -t electoral-api:latest ./backend/electoral-api
# Run:      docker run -p 8080:8080 electoral-api:latest
# =============================================================================

# =============================================================================
# Stage 1: Build con Maven (JDK completo)
# =============================================================================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Build files primero (aprovecha cache de Docker entre builds)
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Cache de dependencias
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Código fuente
COPY src ./src

# Empaqueta el JAR ejecutable (skip tests de Spring Boot)
RUN ./mvnw package -DskipTests -B

# =============================================================================
# Stage 2: Runtime con JRE Alpine (lightweight)
# =============================================================================
# eclipse-temurin:21-jre-alpine ~150 MB, no trae JDK, no trae build tools.
# Solo lo necesario para ejecutar `java -jar`.
FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

# Crear usuario no-root para runtime
RUN addgroup --gid 1000 appgroup && \
    adduser --uid 1000 --ingroup appgroup --shell /bin/sh appuser

# Copiar el JAR ejecutable
COPY --from=builder /app/target/*.jar /app/electoral-api.jar

# Permisos
RUN chown -R appuser:appgroup /app
USER appuser

# Variables de entorno por defecto
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# Health check (actuator de Spring Boot)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
        CMD wget -q -O - http://localhost:8080/actuator/health || exit 1

# Comando: corre el JAR con JVM estándar
# Variables SPRING_* se sobrescriben desde compose.yaml / env.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/electoral-api.jar"]
