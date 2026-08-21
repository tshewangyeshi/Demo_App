# Multi-stage build: compile with the project's own Maven wrapper (same
# build path as local dev and CI — see .github/workflows/test.yml), then
# ship only the JRE + the built jar, not the whole JDK/Maven toolchain.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper and pom first so dependency resolution is cached
# separately from source changes. mvnw is committed to git as mode 644
# (non-executable — a Windows-checkout artifact, same issue fixed for
# GitHub Actions in .github/workflows/test.yml's backend job): COPY
# preserves that mode into the image layer, so ./mvnw fails with
# "Permission denied" (exit 126) without an explicit chmod first —
# found the hard way via a failed Render build.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw
RUN ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# server.port already reads ${PORT:8080} (see application.yml) — Render
# (and Railway) inject PORT at runtime, no extra config needed here.
ENTRYPOINT ["java", "-jar", "app.jar"]
