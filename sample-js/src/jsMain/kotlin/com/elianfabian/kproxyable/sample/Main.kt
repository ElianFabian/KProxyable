package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	runCommonTests(KProxy, "JS")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<JsLocalService>(DemoHandler())
	val localResult = localService.greet("Hello, JS")
	println("Local Result: $localResult")
}
