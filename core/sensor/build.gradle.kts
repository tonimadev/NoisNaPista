plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.devtools.ksp)
}

android {
    namespace = "digital.tonima.noisnapista.core.sensor"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:location"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
