# Multi-stage build: compile with the project's own Maven wrapper (same
# build path as local dev and CI — see .github/workflows/test.yml), then
# ship only the JRE + the built jar, not the whole JDK/Maven toolchain.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper and pom first so dependency resolution is cached
# separately from source changes (mvnw itself needs no chmod — it's
# already executable in the image's filesystem regardless of what git
# recorded, since this runs as a fresh COPY, not a checkout).
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# server.port already reads ${PORT:8080} (see application.yml) — Render
# (and Railway) inject PORT at runtime, no extra config needed here.
ENTRYPOINT ["java", "-jar", "app.jar"]
