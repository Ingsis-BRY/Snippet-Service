plugins {
    id("com.ingsisbry.kotlin-quality") version "2.0.0"
    id("com.ingsisbry.spring-boot-application") version "2.0.0"
    id("com.ingsisbry.jpa") version "2.0.0"
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.azure:azure-storage-blob:12.33.0")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    // Redis Streams: the API publishes analysis jobs, the `worker` profile consumes them
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // /actuator/health (liveness/readiness) for the Swarm healthcheck
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // the auto-configured RestClient.Builder, instrumented with the observation registry (traceparent propagation)
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    // Micrometer Tracing over Brave: traceId/spanId in the MDC and W3C traceparent on outgoing calls; no exporter
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    // New Relic agent API (a no-op without the agent): its trace id goes in the X-Trace-Id response header
    implementation("com.newrelic.agent.java:newrelic-api:9.5.0")
    testImplementation("com.playtika.testcontainers:embedded-azurite:3.1.6")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    // the JPA adapters' queries run against an in-memory database, no container needed
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("com.h2database:h2")

    runtimeOnly("org.postgresql:postgresql")
}
repositories {
    mavenCentral()
}
