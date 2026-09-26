plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.ipirangatech.fidd.core.analytics"
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

// Isola o SDK do Firebase: features e outros core enxergam só AnalyticsTracker/AnalyticsEvent
// (implementation, não api), então não conseguem mandar para o Analytics nada fora dos eventos
// tipados daqui.
dependencies {
    implementation(libs.firebase.analytics)
    implementation(libs.hilt.android)
    implementation(platform(libs.firebase.bom))
    implementation(project(":core:model"))

    ksp(libs.hilt.compiler)

    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}

kover {
    currentProject {
        createVariant("coverage") {
            add("debug")
        }
    }
}
