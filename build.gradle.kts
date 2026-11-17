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
