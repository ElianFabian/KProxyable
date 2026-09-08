package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.FunctionDescriptor
import com.elianfabian.kproxyable.PropertyDescriptor
import com.elianfabian.kproxyable.ProxyHandler
import kotlinx.coroutines.delay

/**
 * A comprehensive ProxyHandler that demonstrates all capabilities of KProxyable.
 * Version 1.1.1: Robust type safety allows natural casting 'as Int'.
 */
class DemoHandler : ProxyHandler {
	private var activeState = false

	override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
		println("▶ [onCall] ${function.name}(${args.joinToString()})")
		return when (function.name) {
			"performAction" -> {
				// Natural Kotlin cast works in WasmJs!
				val id = args[0] as Int
				id > 0
			}
			"jvmSpecificAction" -> (args[0] as Int) * 2
			"kmpSpecificAction" -> "Processed: ${args[0]}"
			"greet" -> "Hello from DemoHandler, ${args[0]}"
			"jsOnly" -> "JS-Interceptors-Enabled: ${args[0]}"
			"nativeOnly" -> "Native-Safe-Result: ${args[0]}"
			else -> null
		}
	}

	override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? {
		println("▶ [onSuspendCall] ${function.name}(${args.joinToString()})")
		if (function.name == "fetchDataAsync") {
			delay(100) 
			return listOf("Result for ${args[0]}", "Extra Data")
		}
		return null
	}

	override fun onGetProperty(property: PropertyDescriptor): Any? {
		println("▶ [onGetProperty] '${property.name}'")
		return when (property.name) {
			"version" -> "1.0.0-DEMO"
			"isActive" -> activeState
			else -> null
		}
	}

	override fun onSetProperty(property: PropertyDescriptor, value: Any?) {
		println("▶ [onSetProperty] '${property.name}' set to $value")
		if (property.name == "isActive") {
            // Natural Boolean cast works too!
			activeState = value as Boolean
		}
	}

	override fun onEquals(other: Any?): Boolean {
		println("▶ [onEquals] Comparing with $other")
		return this === other || (other is DemoHandler)
	}

	override fun onHashCode(): Int {
		println("▶ [onHashCode] Requested")
		return 42
	}

	override fun onToString(): String {
		println("▶ [onToString] Requested")
		return "ComprehensiveDemoHandler[active=$activeState]"
	}
}
