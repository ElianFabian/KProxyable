package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxy
import com.elianfabian.kproxyable.create
import com.elianfabian.kproxyable.generated.installKProxyable

fun main() {
    println("=== KProxyable Wasm-only Sample ===")

    // Initial setup: Installs the generated master registry.
    // This reference ensures the registry is not stripped by DCE.
    installKProxyable()

    println("\n--- Testing Proxy Creation (Automagic) ---")
    try {
        // Use the clean, generic entry point KProxy.
        val localService = KProxy.create<WasmLocalService>(DemoHandler())
        println("Local Proxy Result: ${localService.wasmOnly("Test")}")

        val commonService = KProxy.create<CommonService>(DemoHandler())
        println("Common Proxy Result: ${commonService.performAction(1, "Test")}")
        
        println("\nSUCCESS")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
