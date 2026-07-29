package com.elianfabian.kproxyable

public interface ProxyHandler {
	public fun onCall(function: FunctionDescriptor, args: List<Any?>): Any?
	public suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any?
	public fun onGetProperty(property: PropertyDescriptor): Any?
	public fun onSetProperty(property: PropertyDescriptor, value: Any?)

	public fun onEquals(other: Any?): Boolean
	public fun onHashCode(): Int
	public fun onToString(): String
}
