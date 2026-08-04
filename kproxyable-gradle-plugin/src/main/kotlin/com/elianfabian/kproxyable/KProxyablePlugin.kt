package com.elianfabian.kproxyable

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

class KProxyablePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1. Apply KSP plugin immediately to enable code generation
        project.plugins.apply("com.google.devtools.ksp")

        // 2. React to Kotlin Multiplatform extension if present
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            configureKmp(project, kotlin)
        }

        // 3. Fallback for JVM-only projects
        // We only apply this if it's NOT a multiplatform project to avoid dependency conflicts
        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                project.dependencies.add("implementation", project.kproxyDependency("runtime"))
                project.dependencies.add("ksp", project.kproxyDependency("processor"))
            }
        }

        // 4. Support for JS-only projects
        // Manages the specific KSP configuration naming for the JS target
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
        // Sanitizes the project path to create a unique identifier for module registries
        val moduleName = project.path.split(":", "-")
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .ifEmpty { "root" }
        
        project.extensions.configure(com.google.devtools.ksp.gradle.KspExtension::class.java) {
            arg("kproxyable.moduleName", moduleName)
            
            // AUTOMATIC APPLICATION DETECTION
            // Uses a Provider to detect if this is an application module at the end of configuration.
            // This is used by the processor to determine whether to generate the Master Registry.
            val isAppProvider = project.provider {
                val hasAppPlugin = project.plugins.hasPlugin("com.android.application") || 
                                   project.plugins.hasPlugin("application") ||
                                   project.plugins.hasPlugin("org.gradle.application")
                
                // Detection for Kotlin/JS or KMP executables via reflection on Kotlin internals
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
            
            // LAZY CLASSPATH RESOLUTION
            // Resolves the full compile classpath only during the execution phase.
            // This is required for cross-module "breadcrumb" discovery while avoiding
            // Gradle's "Configuration already resolved" errors during project sync.
            val classpathProvider = project.provider {
                val files = mutableSetOf<java.io.File>()
                
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
        // Add runtime to commonMain so proxies can be used across all targets
        kotlin.sourceSets.getByName("commonMain").dependencies {
            implementation(project.kproxyDependency("runtime"))
        }

        // Add processor to all KSP configurations and fix resource inclusion for JS/Wasm
        // Klibs require explicit resource folder registration for KSP-generated files.
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
                    
                    // Ensure generation happens before resource processing
                    project.tasks.matching { it.name.contains(targetName, ignoreCase = true) && it.name.contains("ProcessResources", ignoreCase = true) }.configureEach {
                        if (project.tasks.findByName(kspTaskName) != null) dependsOn(kspTaskName)
                    }
                    // Ensure generation happens before compilation
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
