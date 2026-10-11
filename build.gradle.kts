plugins {
    id("com.ingsisbry.kotlin-quality") version "2.0.0"
    id("com.ingsisbry.spring-boot-application") version "2.0.0"
    id("com.ingsisbry.jpa") version "2.0.0"
}
dependencies {
    implementation(kotlin("stdlib"))

    runtimeOnly("org.postgresql:postgresql")
}
repositories {
    mavenCentral()
}
