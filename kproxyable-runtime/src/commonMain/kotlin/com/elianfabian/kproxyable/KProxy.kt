package com.elianfabian.kproxyable

public object KProxy {

	/**
	 * Creates an implementation of the [T] interface.
	 *
	 * Note: The [proxy] parameter is internal and will be automatically
	 * replaced at compile-time by the KProxyable Compiler Plugin.
	 */
	public fun <T : Any> create(
		handler: ProxyHandler,
		proxy: Any? = null
	): T {
		if (proxy == null) {
			error("KProxyable Gradle plugin must be applied to your module to use KProxy.create().")
		}
		@Suppress("UNCHECKED_CAST")
		return proxy as T
	}
}
