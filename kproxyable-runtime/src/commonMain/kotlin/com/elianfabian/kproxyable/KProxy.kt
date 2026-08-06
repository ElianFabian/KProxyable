package com.elianfabian.kproxyable

import kotlin.reflect.KClass

/**
 * Main entry point for creating proxies across all platforms.
 */
public object KProxy : KProxyFactory {
    
    /**
     * Manually initializes the master registry.
     * This is required on platforms where automagic discovery is hindered by environment constraints (e.g., WasmJs).
     */
    public fun initialize(factory: KProxyFactory) {
        KProxyRegistryHolder.registry = factory
    }

    override fun <T : Any> findProxy(handler: ProxyHandler, classifier: KClass<T>): T? {
        return KProxyRegistryHolder.registry?.findProxy(handler, classifier)
    }
}
