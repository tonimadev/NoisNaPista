plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

android {
    namespace = "digital.tonima.noisnapista.feature.ranking.bridge"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)
}
