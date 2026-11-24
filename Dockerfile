FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./

COPY src src

RUN --mount=type=secret,id=github_username \
    --mount=type=secret,id=github_token \
    --mount=type=cache,target=/root/.gradle \
    export USERNAME="$(cat /run/secrets/github_username)" && \
    export TOKEN="$(cat /run/secrets/github_token)" && \
    ./gradlew bootJar --no-daemon

ARG NEW_RELIC_AGENT_VERSION=9.5.0

RUN --mount=type=cache,target=/tmp/newrelic-cache \
    curl -fsSL \
    "https://download.newrelic.com/newrelic/java-agent/newrelic-agent/${NEW_RELIC_AGENT_VERSION}/newrelic-java.zip" \
    -o /tmp/newrelic-cache/newrelic-java.zip && \
    mkdir -p /opt/newrelic && \
    cd /opt/newrelic && \
    jar xf /tmp/newrelic-cache/newrelic-java.zip


FROM eclipse-temurin:21-jre

# curl for the Swarm healthcheck (GET /actuator/health/readiness). No HEALTHCHECK here: the same image runs the
# worker profile, which serves no HTTP; the healthcheck is declared per service in the stack.
RUN apt-get update &&     apt-get install -y --no-install-recommends curl &&     rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar
COPY --from=builder /opt/newrelic/newrelic /app/newrelic

EXPOSE 8080

ENTRYPOINT ["java", "-javaagent:/app/newrelic/newrelic.jar", "-jar", "app.jar"]