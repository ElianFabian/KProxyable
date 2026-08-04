package com.elianfabian.kproxyable

import kotlin.reflect.KClass

/**
 * Entry point for creating proxies in JVM-only projects using automagic discovery.
 */
public object KProxyJvm : KProxyFactory {
    private val delegate: KProxyFactory by lazy {
        try {
            val clazz = Class.forName("com.elianfabian.kproxyable.generated.KProxyJvmImpl")
            clazz.getField("INSTANCE").get(null) as KProxyFactory
        } catch (e: Exception) {
            throw IllegalStateException(
                "KProxyable: Master registry 'KProxyJvmImpl' not found. " +
                "Ensure your main application module applies the KProxyable plugin and KSP is configured.", e
            )
        }
    }

    override fun <T : Any> findProxy(handler: ProxyHandler, classifier: KClass<T>): T? {
        return delegate.findProxy(handler, classifier)
    }
}
