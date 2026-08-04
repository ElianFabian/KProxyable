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
                project.dependencies.add("implementation", project.kproxyDependency("runtime"))
                project.dependencies.add("ksp", project.kproxyDependency("processor"))
            }
        }

        // 4. Support for JS-only projects
        project.plugins.withId("org.jetbrains.kotlin.js") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                project.dependencies.add("implementation", project.kproxyDependency("runtime"))
                project.afterEvaluate {
                    val kspMain = project.configurations.findByName("kspKotlinJs")
                    if (kspMain != null) {
                        project.dependencies.add(kspMain.name, project.kproxyDependency("processor"))
                    } else {
                        project.dependencies.add("ksp", project.kproxyDependency("processor"))
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
            
            // Use a Provider for isApp to detect plugins applied later
            val isAppProvider = project.provider {
                val hasAppPlugin = project.plugins.hasPlugin("com.android.application") || 
                                   project.plugins.hasPlugin("application") ||
                                   project.plugins.hasPlugin("org.gradle.application")
                
                // Try to detect Kotlin/JS executable
                val isJsExecutable = try {
                    val kotlin = project.extensions.findByName("kotlin")
                    val targets = kotlin?.javaClass?.methods?.find { it.name == "getTargets" }?.invoke(kotlin) as? Iterable<*>
                    val hasJsExecutable = targets?.any { target ->
                        target?.javaClass?.name?.contains("KotlinJs", ignoreCase = true) == true &&
                        (target.javaClass.methods.find { it.name == "getBinaries" }?.invoke(target) as? Iterable<*>)?.any { binary ->
                            binary?.javaClass?.name?.contains("Executable", ignoreCase = true) == true
                        } == true
                    } == true
                    
                    hasJsExecutable || (project.plugins.hasPlugin("org.jetbrains.kotlin.js") && 
                        (kotlin?.javaClass?.methods?.find { it.name == "getJs" }?.invoke(kotlin)?.let { jsTarget ->
                            (jsTarget.javaClass.methods.find { it.name == "getBinaries" }?.invoke(jsTarget) as? Iterable<*>)?.any { binary ->
                                binary?.javaClass?.name?.contains("Executable", ignoreCase = true) == true
                            }
                        } == true))
                } catch (e: Exception) { false }

                (hasAppPlugin || isJsExecutable).toString()
            }
            arg("kproxyable.isApp", isAppProvider)
            
            // We use a Provider to resolve the classpath lazily
            // This avoids "Cannot change hierarchy" errors
            val classpathProvider = project.provider {
                val files = mutableSetOf<java.io.File>()
                
                // Only look at the most relevant configurations for discovery
                val configNames = listOf(
                    "jvmCompileClasspath", 
                    "debugCompileClasspath", 
                    "compileClasspath",
                    "jsCompileClasspath",
                    "wasmJsCompileClasspath",
                    "kotlinTransitiveCompilePlaceholderJs",
                    "kotlinTransitiveCompilePlaceholderWasmJs"
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
        // Add runtime to commonMain
        kotlin.sourceSets.getByName("commonMain").dependencies {
            implementation(project.kproxyDependency("runtime"))
        }

        // Add processor to all KSP configurations and fix resource inclusion for JS
        kotlin.targets.configureEach {
            if (platformType == KotlinPlatformType.common) return@configureEach
            
            val kspConfigName = if (name == "metadata") {
                "kspCommonMainMetadata"
            } else {
                "ksp${name.replaceFirstChar { it.uppercase() }}"
            }
            
            project.dependencies.add(kspConfigName, project.kproxyDependency("processor"))

            if (platformType.name.contains("js", ignoreCase = true)) {
                compilations.configureEach {
                    val compilationName = name
                    val targetName = target.name
                    val kspTaskName = "kspKotlin${targetName.replaceFirstChar { it.uppercase() }}"
                    
                    val kspResourceDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/$targetName${compilationName.replaceFirstChar { it.uppercase() }}/resources")
                    defaultSourceSet.resources.srcDir(kspResourceDir)
                    
                    project.tasks.matching { it.name.contains(targetName, ignoreCase = true) && it.name.contains("ProcessResources", ignoreCase = true) }.configureEach {
                        if (project.tasks.findByName(kspTaskName) != null) dependsOn(kspTaskName)
                    }
                    project.tasks.matching { it.name == "compileKotlin${targetName.replaceFirstChar { it.uppercase() }}" }.configureEach {
                        if (project.tasks.findByName(kspTaskName) != null) dependsOn(kspTaskName)
                    }
                }
            }
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
