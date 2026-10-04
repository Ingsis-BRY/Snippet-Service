plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()

    maven {
        url = uri("https://maven.pkg.github.com/Ingsis-BRY/gradle-conventions")
        credentials {
            username =
                project.findProperty("gpr.user") as String?
                    ?: System.getenv("USERNAME")

            password =
                project.findProperty("gpr.key") as String?
                    ?: System.getenv("TOKEN")
        }
    }
}

dependencies {
    implementation(
        "com.ingsisbry.kotlin-module:" +
                "com.ingsisbry.kotlin-module.gradle.plugin:1.1.0"
    )

    implementation(
        "com.ingsisbry.spring-boot-application:" +
                "com.ingsisbry.spring-boot-application.gradle.plugin:1.1.0"
    )
}