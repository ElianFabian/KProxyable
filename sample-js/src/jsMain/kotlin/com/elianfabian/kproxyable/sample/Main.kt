package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyJs
import com.elianfabian.kproxyable.create

suspend fun main() {
    println("=== KProxyable JS-only Sample ===")

    println("\n--- Testing Common Service (Cross-Module) ---")
    val service = KProxyJs.create<ComprehensiveService>(DemoHandler())
    val result = service.performAction(42, "JS Data")
    println("Result: $result")

    println("\n--- Testing Local Service (Current Module) ---")
    val localService = KProxyJs.create<JsLocalService>(DemoHandler())
    val localResult = localService.greet("Hello, JS")
    println("Local Result: $localResult")

    println("\n--- Testing Suspend ---")
    val data = service.fetchDataAsync("JS Query")
    println("Async data: $data")

    println("--- Testing Properties ---")
    println("Version: ${service.version}")
    service.isActive = true
    println("Is Active: ${service.isActive}")

    println("--- Testing Any Methods ---")
    println("ToString: $service")
    println("HashCode: ${service.hashCode()}")
}
