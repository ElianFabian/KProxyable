package com.elianfabian.kproxyable

import kotlin.reflect.KClass

public object KProxy : KProxyFactory {
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

    override fun <T : Any> createProxy(handler: ProxyHandler, classifier: KClass<T>): T {
        return delegate.createProxy(handler, classifier)
    }
}
