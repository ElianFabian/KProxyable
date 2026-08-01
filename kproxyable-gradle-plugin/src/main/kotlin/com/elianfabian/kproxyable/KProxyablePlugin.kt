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
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            configureKmp(project, kotlin)
        }

        // 3. Fallback for JVM-only projects
        // We only apply this if it's NOT a multiplatform project to avoid conflicts
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                project.dependencies.add("implementation", project.kproxyDependency("jvm"))
                project.dependencies.add("ksp", project.kproxyDependency("processor"))
            }
        }

        // 4. Common KSP configuration
        val moduleName = project.path.split(":", "-")
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .ifEmpty { "root" }
        
        project.extensions.configure(com.google.devtools.ksp.gradle.KspExtension::class.java) {
            arg("kproxyable.moduleName", moduleName)
            
            // Use a Provider for isApp to detect plugins applied later
            val isAppProvider = project.provider {
                (project.plugins.hasPlugin("com.android.application") || 
                 project.plugins.hasPlugin("application") ||
                 project.plugins.hasPlugin("org.gradle.application")).toString()
            }
            arg("kproxyable.isApp", isAppProvider)
            
            // We use a Provider to resolve the classpath lazily
            // This avoids "Cannot change hierarchy" errors
            val classpathProvider = project.provider {
                val files = mutableSetOf<java.io.File>()
                
                // Only look at the most relevant configurations for discovery
                val configNames = listOf("jvmCompileClasspath", "debugCompileClasspath", "compileClasspath")
                configNames.forEach { name ->
                    project.configurations.findByName(name)?.let { config ->
                        try { files.addAll(config.files) } catch (e: Exception) { }
                    }
                }
                
                files.joinToString(java.io.File.pathSeparator) { it.absolutePath }
            }
            
            arg("kproxyable.classpath", classpathProvider)
        }
    }

    private fun configureKmp(project: Project, kotlin: KotlinMultiplatformExtension) {
        // Add runtime to commonMain
        kotlin.sourceSets.getByName("commonMain").dependencies {
            implementation(project.kproxyDependency("runtime"))
        }

        // Add processor to all KSP configurations
        kotlin.targets.configureEach {
            if (platformType == KotlinPlatformType.common) return@configureEach
            
            val kspConfigName = if (name == "metadata") {
                "kspCommonMainMetadata"
            } else {
                "ksp${name.replaceFirstChar { it.uppercase() }}"
            }
            
            project.dependencies.add(kspConfigName, project.kproxyDependency("processor"))
        }
    }

    private fun Project.kproxyDependency(module: String): Any {
        // Use local project if available (development mode), otherwise use published artifact
        return try {
            project.rootProject.project(":kproxyable-$module")
        } catch (_: Exception) {
            "${BuildConstants.GROUP}:kproxyable-$module:${BuildConstants.VERSION}"
        }
    }
}
