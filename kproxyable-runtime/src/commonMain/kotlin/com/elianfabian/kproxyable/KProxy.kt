package com.elianfabian.kproxyable

import kotlin.reflect.KClass

public interface KProxyFactory {

	public fun <T : Any> createProxy(
		handler: ProxyHandler,
		classifier: KClass<T>,
	): T {
		throw NotImplementedError(
			"""
				No proxy factory implementation found for '${classifier.simpleName ?: classifier}'.
				Ensure KSP is configured and your expect object is annotated with @KProxyRegistry.
			""".trimIndent()
		)
	}
}

public inline fun <reified T : Any> KProxyFactory.create(
	handler: ProxyHandler,
): T {
	return createProxy(handler, T::class)
}
