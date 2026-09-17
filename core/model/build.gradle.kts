plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

dependencies {
    implementation(libs.kotlinx.serialization.core)
}
