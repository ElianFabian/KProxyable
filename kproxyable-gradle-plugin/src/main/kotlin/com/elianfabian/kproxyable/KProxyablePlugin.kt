package com.elianfabian.kproxyable

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.AbstractCopyTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.io.File

/**
 * Gradle plugin for KProxyable.
 * Version 1.1.4: Smart Lazy discovery compatible with the entire Kotlin 2.x lineage.
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
		val extension = project.extensions.getByType(KspExtension::class.java)
		extension.arg("kproxyable.moduleName", moduleName)

		// Calculate the classpath lazily
		val classpathProvider = project.provider {
			val files = mutableSetOf<File>()
			val targetConfigs = project.configurations.filter { 
				val n = it.name.lowercase()
				it.isCanBeResolved && (n.contains("compileclasspath") || n.contains("runtimeclasspath")) && !n.contains("metadata")
			}
			
			targetConfigs.forEach { config ->
				try { files.addAll(config.files) } catch (_: Exception) {}
				try {
					config.incoming.dependencies.filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().forEach { dep ->
						val depProject = dep.dependencyProject
						val resDir = depProject.layout.buildDirectory.dir("generated/ksp").get().asFile
						if (resDir.exists()) {
							resDir.walkTopDown().maxDepth(10).filter { it.name == "resources" }.forEach { files.add(it) }
						}
					}
				} catch (_: Exception) {}
			}
			files.joinToString(File.pathSeparator) { it.absolutePath }
		}

		// Use Reflection to try the modern Provider-based API (KSP 1.0.22+)
		// Fall back to eager calculation in afterEvaluate for older KSP (like 2.0.0)
		try {
			val argMethod = extension.javaClass.getMethod("arg", String::class.java, Provider::class.java)
			argMethod.invoke(extension, "kproxyable.fullClasspath", classpathProvider)
		} catch (_: Exception) {
			project.afterEvaluate {
				extension.arg("kproxyable.fullClasspath", classpathProvider.get())
			}
		}
	}

	private fun configureKmp(project: Project, kotlin: KotlinMultiplatformExtension) {
		kotlin.sourceSets.getByName("commonMain").dependencies {
			implementation(project.kproxyDependency("runtime"))
		}

		kotlin.targets.configureEach {
			if (platformType == KotlinPlatformType.common) return@configureEach
			val targetName = this.name

            val kspConfigName = if (targetName == "metadata") "kspCommonMainMetadata" else "ksp${targetName.replaceFirstChar { it.uppercase() }}"
            project.dependencies.add(kspConfigName, project.kproxyDependency("processor"))

			compilations.configureEach {
				val compilationName = this.name
				val isTest = compilationName == "test"
				val kspTaskName = if (isTest) "kspTestKotlin${targetName.replaceFirstChar { it.uppercase() }}"
				else "kspKotlin${targetName.replaceFirstChar { it.uppercase() }}"

                if (isTest) {
                    project.dependencies.add("ksp${targetName.replaceFirstChar { it.uppercase() }}Test", project.kproxyDependency("processor"))
                }

				val kspResourceDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/$targetName${compilationName.replaceFirstChar { it.uppercase() }}/resources")
				defaultSourceSet.resources.srcDir(kspResourceDir)

				// Task Wiring
				val kspTask = project.tasks.matching { it.name == kspTaskName }
                if (!isTest) {
                    project.tasks.matching { it.name == "compileKotlin${targetName.replaceFirstChar { it.uppercase() }}" }.configureEach { dependsOn(kspTask) }
                }
				project.tasks.matching { it.name == "${targetName}${if (isTest) "Test" else ""}ProcessResources" }.configureEach { dependsOn(kspTask) }

                // Pass test info
                project.extensions.configure(KspExtension::class.java) {
                    arg("kproxyable.isTest.$kspTaskName", isTest.toString())
                }
			}
		}
	}

	private fun Project.kproxyDependency(module: String): Any {
		return try {
			project.rootProject.project(":kproxyable-$module")
		}
		catch (_: Exception) {
			"io.github.elianfabian:kproxyable-$module:1.1.4"
		}
	}
}
