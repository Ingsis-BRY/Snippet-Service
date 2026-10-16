plugins {
    id("com.ingsisbry.kotlin-quality") version "2.0.0"
    id("com.ingsisbry.spring-boot-application") version "2.0.0"
    id("com.ingsisbry.jpa") version "2.0.0"
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.azure:azure-storage-blob:12.33.0")
    testImplementation("com.playtika.testcontainers:embedded-azurite:3.1.6")

    runtimeOnly("org.postgresql:postgresql")
}
repositories {
    mavenCentral()
}
