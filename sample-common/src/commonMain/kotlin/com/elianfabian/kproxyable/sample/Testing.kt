package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyFactory
import com.elianfabian.kproxyable.create

/**
 * Shared test suite to verify CommonService proxy behavior across all platforms.
 */
suspend fun runCommonTests(factory: KProxyFactory, platformName: String) {
	println("=== KProxyable $platformName Sample ===")

	println("\n--- Testing Common Service (Cross-Module) ---")
	val service = factory.create<CommonService>(DemoHandler())

	val result = service.performAction(42, "$platformName Data")
	println("Result of performAction: $result")

	println("\n--- Testing Suspend ---")
	val data = service.fetchDataAsync("$platformName Query")
	println("Async data: $data")

	println("\n--- Testing Properties ---")
	println("Version: ${service.version}")
	println("Is Active (initial): ${service.isActive}")
	service.isActive = true
	println("Is Active (updated): ${service.isActive}")

	println("\n--- Testing Any Methods ---")
	println("ToString: $service")
	println("HashCode: ${service.hashCode()}")
	println("Equals self: ${service == service}")
}
