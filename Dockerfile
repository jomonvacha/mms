FROM openjdk:21-jdk-slim

# Install wget for health checks
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Create logs directory
RUN mkdir -p /app/logs

VOLUME /tmp

COPY target/member-management-system-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=15s --start-period=90s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
