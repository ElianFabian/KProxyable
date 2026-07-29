package com.elianfabian.kproxyable

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

@OptIn(ExperimentalCompilerApi::class)
public class KProxyCommandLineProcessor : CommandLineProcessor {
	override val pluginId: String = KProxyableCompilerPluginRegistrar.PLUGIN_ID

	override val pluginOptions: Collection<CliOption> = listOf(
		CliOption(
			optionName = "enabled",
			valueDescription = "<true|false>",
			description = "Whether to enable the KProxy IR plugin"
		),
		CliOption(
			optionName = "logging",
			valueDescription = "<true|false>",
			description = "Whether to enable debug logging"
		)
	)

	override fun processOption(
		option: AbstractCliOption,
		value: String,
		configuration: CompilerConfiguration
	): Unit = when (option.optionName) {
		"enabled" -> configuration.put(KEY_ENABLED, value.toBoolean())
		"logging" -> configuration.put(KEY_LOGGING, value.toBoolean())
		else -> configuration.put(KEY_ENABLED, true)
	}
}

public val KEY_ENABLED: CompilerConfigurationKey<Boolean> =
	CompilerConfigurationKey("whether the plugin is enabled")

public val KEY_LOGGING: CompilerConfigurationKey<Boolean> =
	CompilerConfigurationKey("whether logging is enabled")
