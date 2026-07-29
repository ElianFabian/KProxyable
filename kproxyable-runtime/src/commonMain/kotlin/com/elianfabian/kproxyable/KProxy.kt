package com.elianfabian.kproxyable

public object KProxy {

	/**
	 * Creates an implementation of the [T] interface.
	 */
	public inline fun <reified T : Any> create(
		handler: ProxyHandler,
	): T {
		error("KProxyable Gradle plugin must be applied to your module to use KProxy.create().")
	}
}
