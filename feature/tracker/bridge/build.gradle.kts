plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.ipirangatech.fidd.feature.tracker.bridge"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
}

kover {
    currentProject {
        createVariant("coverage") {
            add("debug")
        }
    }
}
