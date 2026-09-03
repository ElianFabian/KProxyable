package com.elianfabian.kproxyable

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.AbstractCopyTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

/**
 * Gradle plugin for KProxyable.
 *
 * Automates setup for Multiplatform projects using the Pure KMP (expect/actual) model.
 */
class KProxyablePlugin : Plugin<Project> {
	override fun apply(project: Project) {
		project.plugins.withId("com.google.devtools.ksp") {
			configurePlugin(project)
		}

		project.afterEvaluate {
			if (!project.plugins.hasPlugin("com.google.devtools.ksp")) {
				throw GradleException("KProxyable requires the KSP plugin to be applied.")
			}
			if (!project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
				throw GradleException(
					"KProxyable 1.1.0+ requires the Kotlin Multiplatform plugin. " +
						"For single-target projects, please use kotlin(\"multiplatform\") with a single target."
				)
			}
		}
	}

	private fun configurePlugin(project: Project) {
		project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
			val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)

			// 1. Add mandatory compiler flag to suppress expect/actual stability warnings
			kotlin.targets.all {
				compilations.all {
					compileTaskProvider.configure {
						compilerOptions {
							freeCompilerArgs.add("-Xexpect-actual-classes")
						}
					}
				}
			}

			// 2. Configure Duplicate Handling for Resources (crucial for ServiceLoader files in shared tests)
			project.tasks.withType(AbstractCopyTask::class.java).configureEach {
				if (name.contains("ProcessResources", ignoreCase = true)) {
					duplicatesStrategy = DuplicatesStrategy.INCLUDE
				}
			}

			configureKmp(project, kotlin)
		}

		// 2. Common KSP configuration
		val moduleName = project.path.split(":", "-")
			.filter { it.isNotEmpty() }
			.joinToString("_")
			.ifEmpty { "root" }

		project.extensions.configure(KspExtension::class.java) {
			arg("kproxyable.moduleName", moduleName)

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
							try {
								files.addAll(config.files)
							}
							catch (e: Exception) {
							}
						}
					}
				}
				files.joinToString(java.io.File.pathSeparator) { it.absolutePath }
			}
			arg("kproxyable.classpath", classpathProvider)
		}
	}

	private fun configureKmp(project: Project, kotlin: KotlinMultiplatformExtension) {
		// Automatically add runtime and testing dependencies
		kotlin.sourceSets.getByName("commonMain").dependencies {
			implementation(project.kproxyDependency("runtime"))
		}

		kotlin.sourceSets.getByName("commonTest").dependencies {
			implementation(project.kproxyDependency("runtime"))
			implementation("org.jetbrains.kotlin:kotlin-test")
		}

		kotlin.targets.configureEach {
			if (platformType == KotlinPlatformType.common) return@configureEach

			val targetName = this.name
			val kspConfigName = if (targetName == "metadata") "kspCommonMainMetadata" else "ksp${targetName.replaceFirstChar { it.uppercase() }}"
			val kspTestConfigName = "ksp${targetName.replaceFirstChar { it.uppercase() }}Test"

			project.dependencies.add(kspConfigName, project.kproxyDependency("processor"))
			project.dependencies.add(kspTestConfigName, project.kproxyDependency("processor"))

			// Handle resource linking for Web targets (JS and WasmJs)
			compilations.configureEach {
				val compilationName = this.name
				val isTest = compilationName == "test"
				val kspTaskName = if (isTest) "kspTestKotlin${targetName.replaceFirstChar { it.uppercase() }}"
				else "kspKotlin${targetName.replaceFirstChar { it.uppercase() }}"

				val kspResourceDir = project.layout.buildDirectory.dir("generated/ksp/$targetName/$targetName${compilationName.replaceFirstChar { it.uppercase() }}/resources")
				defaultSourceSet.resources.srcDir(kspResourceDir)

				val processResourcesTaskName = "${targetName}${if (isTest) "Test" else ""}ProcessResources"
				val compileTaskName = "compileKotlin${targetName.replaceFirstChar { it.uppercase() }}${if (isTest) "Test" else ""}"

				project.tasks.matching { it.name == processResourcesTaskName || it.name == compileTaskName }.configureEach {
					dependsOn(project.tasks.matching { it.name == kspTaskName })
				}
			}
		}
	}

	private fun Project.kproxyDependency(module: String): Any {
		return try {
			project.rootProject.project(":kproxyable-$module")
		}
		catch (_: Exception) {
			"${BuildConstants.GROUP}:kproxyable-$module:${BuildConstants.VERSION}"
		}
	}
}
