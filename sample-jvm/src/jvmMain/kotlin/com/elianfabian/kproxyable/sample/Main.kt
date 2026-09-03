package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	runCommonTests(KProxy, "JVM")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<JvmLocalService>(DemoHandler())
	val localResult = localService.jvmSpecificAction(100)
	println("Local Result: $localResult")
}
