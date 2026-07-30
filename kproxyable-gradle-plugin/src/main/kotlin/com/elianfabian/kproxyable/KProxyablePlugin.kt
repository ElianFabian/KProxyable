package com.elianfabian.kproxyable

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class KProxyablePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply KSP plugin immediately
        project.plugins.apply("com.google.devtools.ksp")

        // 2. React to Kotlin Multiplatform extension
        project.extensions.findByType(KotlinMultiplatformExtension::class.java)?.let { kotlin ->
            configureKmp(project, kotlin)
        } ?: project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            configureKmp(project, kotlin)
        }

        // 3. Fallback for JVM-only projects
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.dependencies.add("implementation", project.project(":kproxyable-runtime"))
            project.dependencies.add("ksp", project.project(":kproxyable-processor"))
        }
    }

    private fun configureKmp(project: Project, kotlin: KotlinMultiplatformExtension) {
        // Add runtime to commonMain
        kotlin.sourceSets.getByName("commonMain").dependencies {
            implementation(project.project(":kproxyable-runtime"))
        }

        // Add processor to all KSP configurations
        kotlin.targets.configureEach {
            if (platformType == KotlinPlatformType.common) return@configureEach
            
            val kspConfigName = if (name == "metadata") {
                "kspCommonMainMetadata"
            } else {
                "ksp${name.replaceFirstChar { it.uppercase() }}"
            }
            
            project.dependencies.add(kspConfigName, project.project(":kproxyable-processor"))
        }
    }
}
