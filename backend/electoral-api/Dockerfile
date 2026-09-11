# Build stage (the default image path keeps the existing production behavior).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

# Verification target for local/CI environments that provide the required test
# services. The formatting gate is run separately by CI because this image does
# not include the repository's Git metadata needed by Spotless ratcheting.
FROM builder AS verify
RUN mvn -B verify -Dspotless.check.skip=true

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/electoral-api-*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
