# syntax=docker/dockerfile:1.7
#
# Multi-stage build for mms-service.
#
#   Stage 1 (builder): runs the Maven build inside the image so that
#     `docker compose build mms-service` works without a local Maven install.
#   Stage 2 (runtime): minimal JRE image, non-root user, Spring Actuator
#     healthcheck on /actuator/health.
#
# Build context must be the repository root so Maven can resolve the parent
# POM and sibling modules:
#     docker build -f mms-service/Dockerfile -t mms/mms-service:latest .
#
# docker-compose.yml does this for you via `build.context: .` +
# `build.dockerfile: mms-service/Dockerfile`.

# ─── Stage 1: build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25-alpine AS builder

WORKDIR /build

# Bring in the parent POM and service POM first so dependency resolution
# can be cached across code-only changes.
COPY pom.xml ./
COPY mms-service/pom.xml        mms-service/pom.xml

# Warm the local repo. -N keeps this to the parent; the go-offline attempt on
# the child is best-effort.
RUN mvn -B -N -ntp dependency:resolve-plugins \
 && mvn -B -ntp -pl mms-service -am dependency:go-offline || true

# Copy sources after deps — this layer invalidates on any code change.
COPY mms-service/src mms-service/src

RUN mvn -B -ntp -pl mms-service -am package -DskipTests \
 && cp mms-service/target/mms-service-*.jar /build/app.jar


# ─── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S -G app -H -D app

WORKDIR /app
RUN mkdir -p /app/logs && chown -R app:app /app

COPY --from=builder --chown=app:app /build/app.jar /app/app.jar

USER app

EXPOSE 8081

# Spring Boot Actuator health endpoint. Exposed in application.yml under
# management.endpoints.web.exposure.include.
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
