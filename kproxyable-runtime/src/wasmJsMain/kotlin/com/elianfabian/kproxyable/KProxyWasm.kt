package com.elianfabian.kproxyable

import kotlin.reflect.KClass

/**
 * Entry point for creating proxies in Kotlin/WasmJs projects using automagic discovery.
 */
public object KProxyWasm : KProxyFactory {
    private val delegate: KProxyFactory by lazy {
        KProxyRegistryHolder.registry ?: throw IllegalStateException(
            "KProxyable: Master registry 'KProxyWasmImpl' not found. " +
            "Ensure your main application module applies the KProxyable plugin and KSP is configured."
        )
    }

    override fun <T : Any> findProxy(handler: ProxyHandler, classifier: KClass<T>): T? {
        return delegate.findProxy(handler, classifier)
    }
}
