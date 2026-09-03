package com.elianfabian.kproxyable

import kotlin.reflect.KClass

public interface KProxyFactory {

	public fun <T : Any> createProxy(
		handler: ProxyHandler,
		classifier: KClass<T>,
	): T {
		return findProxy(handler, classifier) ?: throw IllegalArgumentException(
			"No proxy factory implementation found for '${classifier.simpleName ?: classifier}'. " +
				"Ensure the interface is annotated with @KProxyable and KSP is configured."
		)
	}

	public fun <T : Any> findProxy(
		handler: ProxyHandler,
		classifier: KClass<T>,
	): T? = null
}

public inline fun <reified T : Any> KProxyFactory.create(
	handler: ProxyHandler,
): T {
	return createProxy(handler, T::class)
}
