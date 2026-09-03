package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	runCommonTests(KProxy, "KMP (JVM)")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<KmpLocalService>(DemoHandler())
	val localResult = localService.kmpSpecificAction("KMP Input")
	println("Local Result: $localResult")
}
