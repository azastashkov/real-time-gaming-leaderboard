FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle build.gradle gradle.properties ./
COPY leaderboard-app/build.gradle ./leaderboard-app/build.gradle
COPY load-test-client/build.gradle ./load-test-client/build.gradle
RUN chmod +x gradlew && ./gradlew :load-test-client:dependencies --no-daemon -q

COPY load-test-client/src ./load-test-client/src
RUN ./gradlew :load-test-client:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=builder /workspace/load-test-client/build/libs/load-test-client.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
