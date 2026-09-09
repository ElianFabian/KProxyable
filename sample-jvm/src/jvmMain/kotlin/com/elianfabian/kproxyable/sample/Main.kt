package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	runCommonTests(KProxy, "JVM")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<JvmLocalService>(DemoHandler())
	val localResult = localService.jvmSpecificAction(100)
	val localResultStr = localService.jvmSpecificAction("101")
	println("Local Result: $localResult")
	println("Local Result Str: $localResultStr")
}
