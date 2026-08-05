package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== KProxyable KMP Sample (Native) ===")

    println("\n--- Testing Common Service (Cross-Module) ---")
    val service = KProxy.create<CommonService>(DemoHandler())
    val result = service.performAction(1, "Data")
    println("Result of performAction: $result")

    println("\n--- Testing Local Service (Current Module) ---")
    val localService = KProxy.create<KmpLocalService>(DemoHandler())
    val localResult = localService.kmpSpecificAction("KMP Input")
    println("Local Result: $localResult")

    println("\n--- Testing Suspend ---")
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
