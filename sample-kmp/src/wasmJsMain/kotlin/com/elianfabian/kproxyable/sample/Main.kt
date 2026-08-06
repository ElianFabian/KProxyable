package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxy
import com.elianfabian.kproxyable.create

fun main() {
    println("=== KProxyable KMP Sample (WasmJs) ===")

    println("\n--- Testing Manual KMP Registry (Expect/Actual) ---")
    // This uses the registry generated specifically for the KmpRegistry expect object.
    val service = KmpRegistry.create<CommonService>(DemoHandler())
    val result = service.performAction(42, "KMP Wasm Data")
    println("Result: ${result}")

    println("\n--- Testing Automagic Discovery (KProxy) ---")
    // On WasmJs, we must "touch" the master registry to prevent it from being stripped.
    // Calling installKProxyable() once in main is the best way.
    com.elianfabian.kproxyable.generated.installKProxyable()
    
    val genericService = KProxy.create<CommonService>(DemoHandler())
    println("Result: ${genericService.performAction(42, "Global KProxy")}")

    println("\nSUCCESS")
}
