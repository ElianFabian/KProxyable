package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyJs
import com.elianfabian.kproxyable.create

suspend fun main() {
	println("=== KProxyable KMP Sample (JS) ===")

	println("\n--- Testing Manual Registry (KmpRegistry) ---")
	val service = KmpRegistry.create<CommonService>(DemoHandler())
	println("Result: ${service.performAction(1, "JS Manual")}")

	println("\n--- Testing Automagic Discovery (KProxyJs) ---")
	val automagicService = KProxyJs.create<CommonService>(DemoHandler())
	println("Automagic Result: ${automagicService.performAction(2, "JS Automagic")}")

	println("\n--- Testing Suspend ---")
	val data = service.fetchDataAsync("JS Query")
	println("Async data: $data")
}
