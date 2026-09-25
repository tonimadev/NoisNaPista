plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ipirangatech.fidd.core.testing"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
}

// Test doubles shared by every module's unit tests. Only ever a testImplementation dependency.
dependencies {
    api(libs.androidx.datastore.preferences)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(project(":core:billing"))
    api(project(":core:data"))
    api(project(":core:location"))
    api(project(":core:model"))
    api(project(":core:sensor"))
}
