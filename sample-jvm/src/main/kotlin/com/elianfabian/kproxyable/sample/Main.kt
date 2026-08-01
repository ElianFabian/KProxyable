package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxy
import com.elianfabian.kproxyable.create

suspend fun main() {
    println("=== KProxyable JVM-only Sample ===")
    
    println("\n--- Testing Common Service (Cross-Module) ---")
    val service = KProxy.create<ComprehensiveService>(DemoHandler())
    val result = service.performAction(42, "JVM Data")
    println("Result: $result")

    println("\n--- Testing Local Service (Current Module) ---")
    val localService = KProxy.create<JvmLocalService>(DemoHandler())
    val localResult = localService.jvmSpecificAction(100)
    println("Local Result: $localResult")

    println("\n--- Testing Suspend ---")
    val data = service.fetchDataAsync("JVM Query")
    println("Async data: $data")

    println("--- Testing Properties ---")
    println("Version: ${service.version}")
    service.isActive = true
    println("Is Active: ${service.isActive}")

    println("--- Testing Any Methods ---")
    println("ToString: $service")
    println("HashCode: ${service.hashCode()}")
}
