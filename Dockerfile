# Multi-stage build for a Spring Boot (Java 21) app targeting Cloud Run
# 1) Build stage
FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Leverage Docker layer caching
COPY pom.xml .
# Download dependencies (no BuildKit cache mounts to stay compatible with classic Docker builder)
RUN mvn -q -e -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN mvn -q -e -DskipTests package

# 2) Runtime stage
FROM eclipse-temurin:21-jre

# Create non-root user
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

WORKDIR /app

# Copy the fat jar from the builder stage
COPY --from=builder /workspace/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Environment defaults (overridable at deploy time)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=prod

# Cloud Run provides PORT; Spring reads it via server.port=${PORT}
EXPOSE 8080

# Health check endpoint (optional); update if your app exposes a different one
# HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
#   CMD wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
