pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven {
            url =
                uri(
                    "https://maven.pkg.github.com/Ingsis-BRY/gradle-conventions",
                )
        }
    }
}

rootProject.name = "snippet-service"
