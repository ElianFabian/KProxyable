package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
	runCommonTests(KProxy, "KMP (Native)")

	println("\n--- Testing Local Service (Current Module) ---")
	val localService = KProxy.create<NativePlatformService>(DemoHandler())
	val localResult = localService.nativeOnly("Native Input")
	println("Local Result: $localResult")
}
