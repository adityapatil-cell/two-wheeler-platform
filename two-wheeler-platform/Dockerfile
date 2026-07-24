# ─────────────────────────────────────────────────────────────────────────────
# ONE Dockerfile shared by the 4 services in THIS repo:
# gateway, web-bff, platform-service, workshop-service.
# (towing-service, location-service, marketplace-service live in their own
# separate repos with their own Dockerfile.)
#
# Why one file works: this is a Maven multi-module reactor project. Every
# service depends on the "common" module, so we build the whole reactor and
# just pick out the jar for whichever service we're targeting via SERVICE_NAME.
#
# Place this file at the REPO ROOT (two-wheeler-platform/Dockerfile).
# ─────────────────────────────────────────────────────────────────────────────

# ─── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom files first -> lets Docker cache dependency downloads
COPY pom.xml .
COPY common/pom.xml common/
COPY gateway/pom.xml gateway/
COPY web-bff/pom.xml web-bff/
COPY workshop-service/pom.xml workshop-service/
COPY platform-service/pom.xml platform-service/
RUN mvn -B dependency:go-offline || true

# Now copy actual source code
COPY common common
COPY gateway gateway
COPY web-bff web-bff
COPY workshop-service workshop-service
COPY platform-service platform-service

# Which service's jar to build (passed in from docker-compose)
ARG SERVICE_NAME
RUN mvn -B -pl ${SERVICE_NAME} -am clean package -DskipTests

# ─── Stage 2: Run (small final image) ───────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
ARG SERVICE_NAME
WORKDIR /app
COPY --from=build /app/${SERVICE_NAME}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]