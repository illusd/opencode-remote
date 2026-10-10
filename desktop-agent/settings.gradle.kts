pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.namespace == "com.github.johnrengelman") {
                useModule("com.github.johnrengelman.shadow:shadow:8.1.1")
            }
        }
    }
}

rootProject.name = "opencode-desktop-agent"