plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ipirangatech.fidd.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

// Test doubles shared by every module's unit tests. Only ever a testImplementation dependency.
dependencies {
    api(project(":core:model"))
    api(project(":core:data"))
    api(project(":core:location"))
    api(project(":core:sensor"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.datastore.preferences)
}
