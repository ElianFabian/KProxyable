package com.elianfabian.kproxyable

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.AbstractCopyTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File

/**
 * Gradle plugin for KProxyable.
 * Version 1.1.2: Stable cross-module discovery.
 */
class KProxyablePlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.plugins.withId("com.google.devtools.ksp") {
			configurePlugin(project)
		}
	}

	private fun configurePlugin(project: Project) {
		val moduleName = project.path.split(":", "-").filter { it.isNotEmpty() }.joinToString("_").ifEmpty { "root" }

		project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
			val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

			kotlin.targets.all {
				compilations.all {
					compileTaskProvider.configure {
						compilerOptions {
							freeCompilerArgs.add("-Xexpect-actual-classes")
						}
					}
				}
			}

			project.tasks.withType(AbstractCopyTask::class.java).configureEach {
				if (name.contains("ProcessResources", ignoreCase = true)) {
					duplicatesStrategy = DuplicatesStrategy.INCLUDE
				}
			}

			configureKmp(project, kotlin)
		}

        // Global KSP setup
        project.extensions.configure(KspExtension::class.java) {
            arg("kproxyable.moduleName", moduleName)

            val classpathProvider = project.provider {
                val files = mutableSetOf<File>()
                val configNames = listOf(
                    "jvmCompileClasspath", "debugCompileClasspath", "compileClasspath",
                    "jsCompileClasspath", "wasmJsCompileClasspath",
                    "kotlinTransitiveCompilePlaceholderJs", "kotlinTransitiveCompilePlaceholderWasmJs"
                )
                configNames.forEach { name ->
                    project.configurations.findByName(name)?.let { config ->
                        if (config.isCanBeResolved) {
                            try { files.addAll(config.files) } catch (_: Exception) {}
                        }
                    }
                }
                files.joinToString(File.pathSeparator) { it.absolutePath }
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
			val targetName = this.name

			compilations.configureEach {
                val compilationName = this.name
				val isTest = compilationName == "test"
                val kspTaskName = if (isTest) "kspTestKotlin${targetName.replaceFirstChar { it.uppercase() }}" 
                                  else "kspKotlin${targetName.replaceFirstChar { it.uppercase() }}"

				val kspResourceDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/$targetName${compilationName.replaceFirstChar { it.uppercase() }}/resources")
				defaultSourceSet.resources.srcDir(kspResourceDir)

                // Task Wiring: Ensure compilation and resources depend on KSP
                val kspTask = project.tasks.matching { it.name == kspTaskName }
                compileTaskProvider.configure { dependsOn(kspTask) }
                
                val processTaskName = "${targetName}${if (isTest) "Test" else ""}ProcessResources"
                project.tasks.matching { it.name == processTaskName }.configureEach { dependsOn(kspTask) }
			}
		}
	}

	private fun Project.kproxyDependency(module: String): Any {
		return try {
			project.rootProject.project(":kproxyable-$module")
		} catch (_: Exception) {
			"io.github.elianfabian:kproxyable-$module:1.1.0"
		}
	}
}
