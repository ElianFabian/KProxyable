package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

suspend fun main() {
	println("=== KProxyable KMP Sample (JVM) ===")

	val service = KProxy.create<ComprehensiveService>(DemoHandler())

	println("--- Testing Functions ---")
	val result = service.performAction(1, "Data")
	println("Result of performAction: $result")

	println("--- Testing Suspend ---")
	val data = service.fetchDataAsync("KMP Query")
	println("Async data: $data")

	println("--- Testing Properties ---")
	println("Version: ${service.version}")
	println("Is Active (initial): ${service.isActive}")
	service.isActive = true
	println("Is Active (updated): ${service.isActive}")

	println("--- Testing Any Methods ---")
	println("ToString: $service")
	println("HashCode: ${service.hashCode()}")
	println("Equals self: ${service == service}")
}
