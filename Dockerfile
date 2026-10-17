# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/target/cold-monitor-simulator.jar ./
USER app
# options come from the environment (COLD_MONITOR_URL, SIM_INTERVAL, ...) or from arguments after the image name
ENTRYPOINT ["java", "-jar", "cold-monitor-simulator.jar"]
