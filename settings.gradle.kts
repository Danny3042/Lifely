rootProject.name = "HealthApp"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        // Standard public repositories first
        google()
        mavenCentral()

        // Optional private GitHub Packages repository (kept after public repos)
        maven {
            url = uri("https://maven.pkg.github.com/vitoksmile/HealthKMP")
            name = "GitHubPackages"
            credentials {
                val properties = java.util.Properties()
                properties.load(file("local.properties").inputStream())
                username = properties["GITHUB_USERNAME"].toString()
                password = properties["GITHUB_TOKEN"].toString()
            }
        }
    }
}

// Include projects at top-level
include(":composeApp")
