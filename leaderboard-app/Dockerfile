FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY leaderboard-app/build.gradle ./leaderboard-app/build.gradle
COPY load-test-client/build.gradle ./load-test-client/build.gradle
RUN chmod +x gradlew && ./gradlew :leaderboard-app:dependencies --no-daemon -q

COPY leaderboard-app/src ./leaderboard-app/src
RUN ./gradlew :leaderboard-app:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/leaderboard-app/build/libs/leaderboard-app.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
