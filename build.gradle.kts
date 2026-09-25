// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.secrets.gradle.plugin) apply false
    alias(libs.plugins.spotless)
}

// Mesmo gate de estilo do Kairos: `./gradlew detekt spotlessApply sortDependencies`.
// Formatação: ktlint via Spotless, regras no .editorconfig. Análise estática: config/detekt/detekt.yml.
apply(from = "spotless.gradle")

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
}

// Aggregated unit-test coverage for every module (see CLAUDE.md, "Testes"):
//   ./gradlew koverHtmlReportCoverage     -> build/reports/kover/htmlCoverage/index.html
//   ./gradlew koverVerifyCoverage         -> fails below the minimum bounds (see verify below)
// Each module declares a "coverage" variant (its debug build, or jvm for :core:model); this
// merges them.
dependencies {
    kover(project(":app"))
    kover(project(":core:ads"))
    kover(project(":core:billing"))
    kover(project(":core:data"))
    kover(project(":core:location"))
    kover(project(":core:model"))
    kover(project(":core:network"))
    kover(project(":core:sensor"))
    kover(project(":core:ui"))
    kover(project(":feature:map:bridge"))
    kover(project(":feature:map:impl"))
    kover(project(":feature:onboarding"))
    kover(project(":feature:ranking:bridge"))
    kover(project(":feature:ranking:impl"))
    kover(project(":feature:tracker:bridge"))
    kover(project(":feature:tracker:impl"))
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
                    "*_Factory",
                    "*_Factory\$*",
                    "*_MembersInjector",
                    "*_HiltModules*",
                    "*_Provide*Factory*",
                    "Hilt_*",
                    "*_GeneratedInjector",
                    "*Hilt_*",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                    "*_Impl",
                    "*_Impl\$*",
                    "*Dao_Impl*",
                    "*JsonAdapter",
                    "*.ComposableSingletons*",
                    "*.BuildConfig",
                    "*.R",
                    "*.R\$*",
                    "*_AssistedFactory*",
                    "*_Hilt*",
                    "*\$\$serializer",
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

val sortDependencies by tasks.registering {
    group = "Verification"
    description = "Checks and sorts dependencies and plugins in build.gradle.kts files with spacing between groups."

    val buildFilesFromConfig = project.allprojects.map { it.file("build.gradle.kts") }.filter { it.exists() }
    inputs.files(buildFilesFromConfig)

    doLast {
        inputs.files.forEach { file ->
            val lines = file.readLines()
            val newLines = mutableListOf<String>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.trim().startsWith("plugins {") || line.trim().startsWith("dependencies {")) {
                    val isDependencies = line.trim().startsWith("dependencies {")
                    newLines.add(line)
                    val blockLines = mutableListOf<String>()
                    i++
                    var openBraces = 1
                    while (i < lines.size && openBraces > 0) {
                        val currentLine = lines[i]
                        openBraces += currentLine.count { it == '{' }
                        openBraces -= currentLine.count { it == '}' }
                        if (openBraces > 0) {
                            blockLines.add(currentLine)
                            i++
                        }
                    }

                    if (isDependencies) {
                        val groups = mutableMapOf<String, MutableList<String>>()
                        val other = mutableListOf<String>()

                        var currentComments = mutableListOf<String>()

                        blockLines.forEach { bl ->
                            val trimmed = bl.trim()
                            if (trimmed.isEmpty()) return@forEach

                            if (trimmed.startsWith("//")) {
                                currentComments.add(bl)
                            } else {
                                val match = Regex("^([a-zA-Z]+)\\(.*\\)$").find(trimmed)
                                val groupName = match?.groupValues?.get(1) ?: "other"

                                val entry = (currentComments + bl).joinToString("\n")
                                if (groupName != "other") {
                                    groups.getOrPut(groupName) { mutableListOf() }.add(entry)
                                } else {
                                    other.add(entry)
                                }
                                currentComments = mutableListOf()
                            }
                        }

                        val sortedGroupNames = groups.keys.sorted()
                        sortedGroupNames.forEachIndexed { index, name ->
                            val sortedEntries = groups[name]!!.sortedBy { it.trim().lowercase() }
                            newLines.addAll(sortedEntries)
                            if (index < sortedGroupNames.size - 1 || other.isNotEmpty()) {
                                if (newLines.last().isNotBlank()) {
                                    newLines.add("")
                                }
                            }
                        }
                        if (other.isNotEmpty()) {
                            newLines.addAll(other.sortedBy { it.trim().lowercase() })
                        }
                    } else {
                        // For plugins, just sort alphabetically but keep comments
                        val entries = mutableListOf<String>()
                        var currentComments = mutableListOf<String>()
                        blockLines.forEach { bl ->
                            val trimmed = bl.trim()
                            if (trimmed.isEmpty()) return@forEach
                            if (trimmed.startsWith("//")) {
                                currentComments.add(bl)
                            } else {
                                entries.add((currentComments + bl).joinToString("\n"))
                                currentComments = mutableListOf()
                            }
                        }
                        newLines.addAll(entries.sortedBy { it.trim().lowercase() })
                    }

                    if (i < lines.size) newLines.add(lines[i])
                } else {
                    newLines.add(line)
                }
                i++
            }
            file.writeText(newLines.joinToString("\n") + "\n")
        }
    }
}
