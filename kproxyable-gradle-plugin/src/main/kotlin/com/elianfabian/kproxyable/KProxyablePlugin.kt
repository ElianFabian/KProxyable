package com.elianfabian.kproxyable

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Gradle plugin for KProxyable.
 *
 * This plugin automates the setup of processor and runtime dependencies for KProxyable,
 * supporting JVM-only, JS-only, and Multiplatform projects.
 *
 * Fully compatible with Gradle Configuration Cache.
 */
class KProxyablePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Verify KSP plugin is applied by the user
        // We do NOT apply it ourselves to avoid forcing a specific version (Out-of-the-Box KMP practice)
        project.plugins.withId("com.google.devtools.ksp") {
            configurePlugin(project)
        }
        
        project.afterEvaluate {
            if (!project.plugins.hasPlugin("com.google.devtools.ksp")) {
                throw GradleException(
                    "KProxyable requires the KSP plugin to be applied. " +
                    "Please add id(\"com.google.devtools.ksp\") version \"<matching-kotlin-version>\" to your build.gradle.kts."
                )
            }
        }
    }

    private fun configurePlugin(project: Project) {
        // 2. Multiplatform Support
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            configureKmp(project, kotlin)
        }

        // 3. JVM-only Support
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                project.dependencies.add("implementation", project.kproxyDependency("runtime"))
                project.dependencies.add("ksp", project.kproxyDependency("processor"))
                project.dependencies.add("kspTest", project.kproxyDependency("processor"))
            }
        }

        // 4. JS-only Support
        project.plugins.withId("org.jetbrains.kotlin.js") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                project.dependencies.add("implementation", project.kproxyDependency("runtime"))
                
                // Use lazy configuration to find the correct KSP target
                project.configurations.configureEach {
                    if (name == "kspKotlinJs" || name == "kspTestKotlinJs") {
                        project.dependencies.add(name, project.kproxyDependency("processor"))
                    }
                }
            }
        }

        // 5. Common KSP configuration
        val moduleName = project.path.split(":", "-")
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .ifEmpty { "root" }
        
        project.extensions.configure(com.google.devtools.ksp.gradle.KspExtension::class.java) {
            arg("kproxyable.moduleName", moduleName)
            
            // Uses a Provider for Configuration Cache compatibility
            val isAppProvider = project.provider {
                val hasAppPlugin = project.plugins.hasPlugin("com.android.application") || 
                                   project.plugins.hasPlugin("application") ||
                                   project.plugins.hasPlugin("org.gradle.application")
                
                if (hasAppPlugin) return@provider "true"

                // Detection for Kotlin/JS or KMP executables
                try {
                    val kotlin = project.extensions.findByName("kotlin")
                    val targets = kotlin?.javaClass?.methods?.find { it.name == "getTargets" }?.invoke(kotlin) as? Iterable<*>
                    val hasExecutable = targets?.any { target ->
                        val binaries = target?.javaClass?.methods?.find { it.name == "getBinaries" }?.invoke(target) as? Iterable<*>
                        binaries?.any { binary ->
                            binary?.javaClass?.name?.contains("Executable", ignoreCase = true) == true
                        } == true
                    } ?: false
                    
                    hasExecutable.toString()
                } catch (e: Exception) { "false" }
            }
            arg("kproxyable.isApp", isAppProvider)
            
            val classpathProvider = project.provider {
                val files = mutableSetOf<java.io.File>()
                val configNames = listOf(
                    "jvmCompileClasspath", "debugCompileClasspath", "compileClasspath",
                    "jsCompileClasspath", "wasmJsCompileClasspath",
                    "kotlinTransitiveCompilePlaceholderJs", "kotlinTransitiveCompilePlaceholderWasmJs"
                )
                configNames.forEach { name ->
                    project.configurations.findByName(name)?.let { config ->
                        if (config.isCanBeResolved) {
                            try { files.addAll(config.files) } catch (e: Exception) { }
                        }
                    }
                }
                files.joinToString(java.io.File.pathSeparator) { it.absolutePath }
            }
            arg("kproxyable.classpath", classpathProvider)
        }
    }

    private fun configureKmp(project: Project, kotlin: KotlinMultiplatformExtension) {
        kotlin.sourceSets.getByName("commonMain").dependencies {
            implementation(project.kproxyDependency("runtime"))
        }

        kotlin.targets.configureEach {
            if (platformType == KotlinPlatformType.common) return@configureEach
            
            val kspConfigName = if (name == "metadata") {
                "kspCommonMainMetadata"
            } else {
                "ksp${name.replaceFirstChar { it.uppercase() }}"
            }
            
            val kspTestConfigName = if (name == "metadata") {
                "kspTestCommonMainMetadata" // Usually not needed, but for completeness
            } else {
                "ksp${name.replaceFirstChar { it.uppercase() }}Test"
            }
            
            project.dependencies.add(kspConfigName, project.kproxyDependency("processor"))
            project.dependencies.add(kspTestConfigName, project.kproxyDependency("processor"))

            // Handle resource linking for Web targets (lazy configuration)
            if (platformType.name.contains("js", ignoreCase = true)) {
                compilations.configureEach {
                    val compilationName = name
                    val targetName = target.name
                    val kspTaskName = "kspKotlin${targetName.replaceFirstChar { it.uppercase() }}"
                    
                    val kspResourceDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/$targetName${compilationName.replaceFirstChar { it.uppercase() }}/resources")
                    defaultSourceSet.resources.srcDir(kspResourceDir)
                    
                    project.tasks.configureEach {
                        if ((name.contains(targetName, ignoreCase = true) && name.contains("ProcessResources", ignoreCase = true)) ||
                            (name == "compileKotlin${targetName.replaceFirstChar { it.uppercase() }}")) {
                            dependsOn(kspTaskName)
                        }
                    }
                }
            }
        }
    }

    private fun Project.kproxyDependency(module: String): Any {
        return try {
            project.rootProject.project(":kproxyable-$module")
        } catch (_: Exception) {
            "${BuildConstants.GROUP}:kproxyable-$module:${BuildConstants.VERSION}"
        }
    }
}
