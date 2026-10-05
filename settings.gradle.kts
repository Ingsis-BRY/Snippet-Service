pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()

        maven {
            url =
                uri(
                    "https://maven.pkg.github.com/Ingsis-BRY/gradle-conventions",
                )

            credentials {
                username =
                    providers
                        .gradleProperty("gpr.user")
                        .orElse(providers.environmentVariable("USERNAME"))
                        .orNull

                password =
                    providers
                        .gradleProperty("gpr.key")
                        .orElse(providers.environmentVariable("TOKEN"))
                        .orNull
            }
        }
    }
}

rootProject.name = "snippet-service"
