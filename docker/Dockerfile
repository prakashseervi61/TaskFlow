# syntax=docker/dockerfile:1
#
# One image definition serves both services; pass the Maven module to build.
#   docker build -f docker/Dockerfile --build-arg MODULE=backend .
#   docker build -f docker/Dockerfile --build-arg MODULE=worker  .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /workspace

# Resolve dependencies first so source edits do not re-download the world.
COPY pom.xml .
COPY core/pom.xml core/
COPY backend/pom.xml backend/
COPY worker/pom.xml worker/
RUN mvn -B -q dependency:go-offline -DexcludeArtifactIds=taskflow-core || true

COPY core core
COPY backend backend
COPY worker worker
RUN mvn -B -q clean package -DskipTests -pl ${MODULE} -am

FROM eclipse-temurin:21-jre-jammy AS runtime
ARG MODULE
WORKDIR /app

RUN useradd --system --create-home taskflow
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
RUN chown -R taskflow:taskflow /app
USER taskflow

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]