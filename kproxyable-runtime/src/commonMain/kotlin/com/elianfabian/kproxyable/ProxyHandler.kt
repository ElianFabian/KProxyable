package com.elianfabian.kproxyable

/**
 * Interceptor interface responsible for handling invocation events on a proxied instance created by `KProxyable`.
 *
 * Implementations define custom behavior when functions are called, properties are accessed or modified,
 * or standard [Any] methods ([equals], [hashCode], [toString]) are evaluated on the proxy instance.
 */
public interface ProxyHandler {

	/**
	 * Intercepts a synchronous function call on the proxy.
	 *
	 * @param function The metadata descriptor of the function being invoked.
	 * @param args The positional arguments passed to the function call.
	 * @return The result value to be returned by the invoked function.
	 */
	public fun onCall(function: FunctionDescriptor, args: List<Any?>): Any?

	/**
	 * Intercepts an asynchronous (`suspend`) function call on the proxy.
	 *
	 * @param function The metadata descriptor of the suspend function being invoked.
	 * @param args The positional arguments passed to the function call.
	 * @return The result value to be returned by the invoked suspend function.
	 */
	public suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any?

	/**
	 * Intercepts property getter access on the proxy.
	 *
	 * @param property The metadata descriptor of the accessed property.
	 * @return The value to return for the property getter.
	 */
	public fun onGetProperty(property: PropertyDescriptor): Any?

	/**
	 * Intercepts a property setter assignment on the proxy.
	 *
	 * @param property The metadata descriptor of the modified property.
	 * @param value The value being assigned to the property.
	 */
	public fun onSetProperty(property: PropertyDescriptor, value: Any?)

	/**
	 * Intercepts the [Any.equals] method on the proxy.
	 *
	 * @param other The object to compare with the proxy instance.
	 * @return `true` if the objects are considered equal, `false` otherwise.
	 */
	public fun onEquals(other: Any?): Boolean

	/**
	 * Intercepts the [Any.hashCode] method on the proxy.
	 *
	 * @return The hash code value for the proxy instance.
	 */
	public fun onHashCode(): Int

	/**
	 * Intercepts the [Any.toString] method on the proxy.
	 *
	 * @return A string representation of the proxy instance.
	 */
	public fun onToString(): String
}
