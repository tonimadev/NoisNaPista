pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FIDD"
include(":app")
include(":core:model")
include(":core:ui")
include(":core:location")
include(":core:sensor")
include(":core:data")
include(":core:network")
include(":core:testing")
include(":feature:tracker:bridge")
include(":feature:tracker:impl")
include(":feature:onboarding")
include(":feature:map:bridge")
include(":feature:map:impl")
include(":feature:ranking:bridge")
include(":feature:ranking:impl")
