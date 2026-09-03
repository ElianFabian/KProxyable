package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	runCommonTests(KProxy, "KMP (JS)")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<JsPlatformService>(DemoHandler())
	val localResult = localService.jsOnly("JS Input")
	println("Local Result: $localResult")
}
