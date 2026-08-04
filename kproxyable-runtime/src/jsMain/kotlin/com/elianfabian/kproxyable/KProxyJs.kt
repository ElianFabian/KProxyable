package com.elianfabian.kproxyable

import kotlin.reflect.KClass

/**
 * Entry point for creating proxies in Kotlin/JS-only projects using automagic discovery.
 */
public object KProxyJs : KProxyFactory {
    private val delegate: KProxyFactory by lazy {
        findImplementation() ?: throw IllegalStateException(
            "KProxyable: Master registry 'KProxyJsImpl' not found. " +
            "Ensure your main application module applies the KProxyable plugin and KSP is configured."
        )
    }

    override fun <T : Any> findProxy(handler: ProxyHandler, classifier: KClass<T>): T? {
        return delegate.findProxy(handler, classifier)
    }

    private fun findImplementation(): KProxyFactory? {
        return try {
            val impl = js("""
                (typeof getKProxyJsImpl === 'function') ? getKProxyJsImpl() :
                (typeof globalThis !== 'undefined' && typeof globalThis.getKProxyJsImpl === 'function') ? globalThis.getKProxyJsImpl() :
                (typeof require === 'function' && require.main && require.main.exports && typeof require.main.exports.getKProxyJsImpl === 'function') ? require.main.exports.getKProxyJsImpl() :
                null
            """)
            impl as? KProxyFactory
        } catch (_: Exception) {
            null
        }
    }
}
