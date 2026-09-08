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
 * Version 1.1.1: Automagic dependency injection and cross-module discovery.
 */
class KProxyablePlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.plugins.withId("com.google.devtools.ksp") {
			configurePlugin(project)
		}
	}

	private fun configurePlugin(project: Project) {
		val moduleName = project.path.split(":", "-").filter { it.isNotEmpty() }.joinToString("_").ifEmpty { "root" }

		// 1. KMP Configuration
		project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
			val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
			configureKmp(project, kotlin)
		}

		// 2. Pure JVM Configuration
		project.plugins.withId("org.jetbrains.kotlin.jvm") {
			project.dependencies.add("implementation", project.kproxyDependency("runtime"))
			project.dependencies.add("ksp", project.kproxyDependency("processor"))
            project.dependencies.add("testImplementation", project.kproxyDependency("runtime"))
            project.dependencies.add("kspTest", project.kproxyDependency("processor"))
		}

		// 3. Pure JS Configuration
		project.plugins.withId("org.jetbrains.kotlin.js") {
			project.dependencies.add("implementation", project.kproxyDependency("runtime"))
			project.dependencies.add("ksp", project.kproxyDependency("processor"))
		}

		// 4. Global resource fixes for tasks
		project.tasks.withType(AbstractCopyTask::class.java).configureEach {
			if (name.contains("ProcessResources", ignoreCase = true)) {
				duplicatesStrategy = DuplicatesStrategy.INCLUDE
			}
		}

		// 5. Global KSP setup
		project.extensions.configure(KspExtension::class.java) {
			arg("kproxyable.moduleName", moduleName)

			val classpathProvider = project.provider {
				val files = mutableSetOf<File>()
				project.configurations.all {
					if (isCanBeResolved && (name.contains("CompileClasspath") || name.contains("RuntimeClasspath"))) {
						try { files.addAll(this.files) } catch (_: Exception) {}
					}
				}

				project.configurations.all {
                    if (isCanBeResolved) {
                        incoming.dependencies.filterIsInstance<org.gradle.api.artifacts.ProjectDependency>().forEach { dep ->
                            val depProject = dep.dependencyProject
                            val resDir = depProject.layout.buildDirectory.dir("generated/ksp").get().asFile
                            if (resDir.exists()) {
                                resDir.walkTopDown().maxDepth(10).filter { it.name == "resources" }.forEach { files.add(it) }
                            }
                        }
                    }
				}
				files.joinToString(File.pathSeparator) { it.absolutePath }
			}
			arg("kproxyable.fullClasspath", classpathProvider)
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

				// Task Wiring - Avoid circular dependencies by only wiring main compilation
				val kspTask = project.tasks.matching { it.name == kspTaskName }
                if (!isTest) {
                    project.tasks.matching { it.name == "compileKotlin${targetName.replaceFirstChar { it.uppercase() }}" }.configureEach { dependsOn(kspTask) }
                }
				project.tasks.matching { it.name == "${targetName}${if (isTest) "Test" else ""}ProcessResources" }.configureEach { dependsOn(kspTask) }

                // Argument Provider for isTest
                project.tasks.matching { it.name == kspTaskName }.configureEach {
                    try {
                        val getProviders = this::class.java.getMethod("getCommandLineArgumentProviders")
                        @Suppress("UNCHECKED_CAST")
                        val providers = getProviders.invoke(this) as MutableList<Any>
                        providers.add(object : org.gradle.process.CommandLineArgumentProvider {
                            override fun asArguments() = listOf(
                                "plugin:com.google.devtools.ksp.symbol-processing:kproxyable.isTest=$isTest"
                            )
                        })
                    } catch (_: Exception) {}
                }
			}
		}
	}

	private fun Project.kproxyDependency(module: String): Any {
		return try {
			project.rootProject.project(":kproxyable-$module")
		}
		catch (_: Exception) {
			"io.github.elianfabian:kproxyable-$module:1.1.1"
		}
	}
}
