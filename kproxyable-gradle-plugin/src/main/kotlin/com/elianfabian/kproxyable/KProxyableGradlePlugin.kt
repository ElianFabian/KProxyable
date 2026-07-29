package com.elianfabian.kproxyable

import org.gradle.api.Plugin
import org.gradle.api.Project

class KProxyableGradlePlugin : Plugin<Project> {

	companion object {
		const val GROUP_NAME = "com.elianfabian.kproxyable"
		const val COMPILER_ARTIFACT = "kproxyable-compiler-plugin"
		const val PROCESSOR_ARTIFACT = "kproxyable-processor"
		const val COMPILER_PLUGIN_ID = "kproxyPlugin"
		const val VERSION = "1.0.0-SNAPSHOT"
	}

	override fun apply(project: Project) {
		with(project) {
			// 1. Force the compiler plugin dependency to resolve to the local module
			configurations.all {
				if (name.contains("kotlinCompilerPluginClasspath", ignoreCase = true)) {
					project.dependencies.add(name, project.project(":kproxyable-compiler-plugin"))
				}

				resolutionStrategy.dependencySubstitution {
					substitute(module("$GROUP_NAME:$COMPILER_ARTIFACT"))
						.using(project(":kproxyable-compiler-plugin"))
						.because("Local development of kproxyable compiler plugin")
				}
			}

			// 2. Apply the compiler plugin subplugin
			pluginManager.apply(KProxyableCompilerSubPlugin::class.java)

			// 2. Automatically wire KSP processor if KSP is applied in the consuming module
			val hasKspApplied = extensions.findByName("ksp") != null
			if (hasKspApplied) {
				dependencies.add("ksp", project(":kproxyable-processor"))
			}
		}
	}
}
