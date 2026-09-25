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
        // PayWall (compra "Remover anúncios") só é publicada no JitPack.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.tonimadev.PayWall") }
        }
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
include(":core:billing")
include(":core:ads")
include(":feature:tracker:bridge")
include(":feature:tracker:impl")
include(":feature:onboarding")
include(":feature:map:bridge")
include(":feature:map:impl")
include(":feature:ranking:bridge")
include(":feature:ranking:impl")
