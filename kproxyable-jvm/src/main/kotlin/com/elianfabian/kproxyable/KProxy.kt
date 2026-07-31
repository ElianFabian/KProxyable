package com.elianfabian.kproxyable

import kotlin.reflect.KClass

public object KProxy : KProxyFactory {
    private val delegate: KProxyFactory by lazy {
        try {
            val clazz = Class.forName("com.elianfabian.kproxyable.generated.KProxyRegistryImpl")
            clazz.getField("INSTANCE").get(null) as KProxyFactory
        } catch (e: Exception) {
            throw IllegalStateException(
                "KProxyable: Generated registry not found. " +
                "Ensure you have annotated your interfaces with @KProxyable and KSP is configured.", e
            )
        }
    }

    override fun <T : Any> createProxy(handler: ProxyHandler, classifier: KClass<T>): T {
        return delegate.createProxy(handler, classifier)
    }
}
