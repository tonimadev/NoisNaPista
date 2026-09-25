// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.secrets.gradle.plugin) apply false
    alias(libs.plugins.kover)
}

// Aggregated unit-test coverage for every module (see CLAUDE.md, "Testes"):
//   ./gradlew koverHtmlReportCoverage     -> build/reports/kover/htmlCoverage/index.html
//   ./gradlew koverVerifyCoverage         -> fails below the minimum bounds (see verify below)
// Each module declares a "coverage" variant (its debug build, or jvm for :core:model); this
// merges them.
dependencies {
    kover(project(":app"))
    kover(project(":core:model"))
    kover(project(":core:ui"))
    kover(project(":core:location"))
    kover(project(":core:sensor"))
    kover(project(":core:data"))
    kover(project(":core:network"))
    kover(project(":feature:tracker:bridge"))
    kover(project(":feature:tracker:impl"))
    kover(project(":feature:onboarding"))
    kover(project(":feature:map:bridge"))
    kover(project(":feature:map:impl"))
    kover(project(":feature:ranking:bridge"))
    kover(project(":feature:ranking:impl"))
}

kover {
    currentProject {
        createVariant("coverage") {}
    }
    reports {
        filters {
            excludes {
                // Generated code: Hilt/Dagger, Room, Moshi, Compose compiler, Android resources.
                classes(
                    "*_Factory", "*_Factory\$*", "*_MembersInjector", "*_HiltModules*", "*_Provide*Factory*",
                    "Hilt_*", "*_GeneratedInjector", "*Hilt_*", "dagger.hilt.*", "hilt_aggregated_deps.*",
                    "*_Impl", "*_Impl\$*", "*Dao_Impl*", "*JsonAdapter", "*.ComposableSingletons*", "*.BuildConfig", "*.R", "*.R\$*",
                    "*_AssistedFactory*", "*_Hilt*", "*\$\$serializer",
                )
                // @Preview functions only exist for Android Studio's design pane.
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
            }
        }
        // Gate: `./gradlew koverVerifyCoverage` fails below 90% of lines. Branches get a lower
        // floor because the Compose compiler adds skip/restart branches no test can reach.
        verify {
            rule("line coverage") {
                minBound(90, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
            }
            rule("branch coverage") {
                minBound(75, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}
