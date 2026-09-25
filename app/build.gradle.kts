import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.secrets.gradle.plugin)
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

// AdMob: IDs reais no admob.properties da raiz (git-ignored), um bloco de anúncios por ponto de
// inserção para o AdMob separar a receita de cada tela. Arquivo próprio porque o local.properties
// é reescrito pelo Android Studio, e tudo no secrets.properties vira campo no BuildConfig pelo
// plugin Secrets (conflitaria com os campos gerados aqui). Sem eles cai nos IDs de teste oficiais do Google:
// o build funciona, só não gera receita. Debug usa sempre o banner de teste (ver AdUnits.kt).
val admobProperties = Properties().apply {
    rootProject.file("admob.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerId = "ca-app-pub-3940256099942544/9214589741"
val admobBannerKeys = listOf("ADMOB_BANNER_HOME", "ADMOB_BANNER_MAP", "ADMOB_BANNER_HISTORY", "ADMOB_BANNER_RANKING")
fun admobId(key: String): String? = admobProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }

val missingAdmobKeys = (listOf("ADMOB_APP_ID") + admobBannerKeys).filter { admobId(it) == null }
val buildsRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
if (buildsRelease && missingAdmobKeys.isNotEmpty()) {
    logger.warn("w: AdMob sem ID real no admob.properties para $missingAdmobKeys: o release vai exibir anúncios de teste (sem receita).")
}

android {
    namespace = "com.ipirangatech.fidd"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        applicationId = "com.ipirangatech.fidd"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Fallback for the unit-test manifest merge, which the Secrets plugin doesn't reach; the
        // app variants still get the real key from secrets.properties.
        manifestPlaceholders["MAPS_API_KEY"] = "unit-test-placeholder"
        // O MobileAdsInitProvider do SDK derruba o processo se o manifest não tiver App ID.
        manifestPlaceholders["ADMOB_APP_ID"] = admobId("ADMOB_APP_ID") ?: admobTestAppId
        admobBannerKeys.forEach { key ->
            buildConfigField("String", key, "\"${admobId(key) ?: admobTestBannerId}\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        // minSdk 24 não tem java.time (só a partir da API 26): sem isso o Instant.parse do
        // PotholeRepositoryImpl derruba o app no Android 7.x ao carregar os buracos da comunidade.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:ads"))
    implementation(project(":core:billing"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:model"))
    implementation(project(":core:sensor"))
    implementation(project(":core:ui"))
    implementation(project(":feature:map:bridge"))
    implementation(project(":feature:map:impl"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:ranking:bridge"))
    implementation(project(":feature:ranking:impl"))
    implementation(project(":feature:tracker:bridge"))
    implementation(project(":feature:tracker:impl"))

    ksp(libs.hilt.compiler)

    kspTest(libs.hilt.compiler)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(project(":core:testing"))

    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}

kover {
    currentProject {
        createVariant("coverage") {
            add("debug")
        }
    }
}
