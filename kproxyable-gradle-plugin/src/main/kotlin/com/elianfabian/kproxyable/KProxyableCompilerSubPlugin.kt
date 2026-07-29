package com.elianfabian.kproxyable

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

public class KProxyableCompilerSubPlugin : KotlinCompilerPluginSupportPlugin {

	override fun getCompilerPluginId(): String = KProxyableGradlePlugin.COMPILER_PLUGIN_ID

	override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
		println(">>> [DEBUG] KProxyableCompilerSubPlugin.isApplicable for ${kotlinCompilation.name} <<<")
		return true
	}

	// Provide a dummy artifact so the interface requirement is satisfied
	override fun getPluginArtifact(): SubpluginArtifact =
		SubpluginArtifact(
			groupId = KProxyableGradlePlugin.GROUP_NAME,
			artifactId = KProxyableGradlePlugin.COMPILER_ARTIFACT,
			version = KProxyableGradlePlugin.VERSION,
		)

	override fun applyToCompilation(
		kotlinCompilation: KotlinCompilation<*>
	): Provider<List<SubpluginOption>> {
		println(">>> [DEBUG] KProxyableCompilerSubPlugin.applyToCompilation for ${kotlinCompilation.name} <<<")
		return kotlinCompilation.target.project.provider {
			listOf(
				SubpluginOption(key = "enabled", value = "true"),
				SubpluginOption(key = "logging", value = "true")
			)
		}
	}
}
