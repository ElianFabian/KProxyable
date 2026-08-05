package com.elianfabian.kproxyable

/**
 * Marks an interface to enable compile-time proxy generation with `KProxyable`.
 *
 * When applied directly to an interface, the KSP processor generates a proxy implementation
 * that routes function calls, property accesses, and standard [Any] methods to a [ProxyHandler].
 *
 * Can also be used as a **meta-annotation** on custom annotations (e.g., `@HttpClient`) to automatically
 * trigger proxy generation for any interface annotated with those custom annotations.
 */
@Target(
	AnnotationTarget.CLASS,
	AnnotationTarget.ANNOTATION_CLASS,
)
@Retention(AnnotationRetention.BINARY)
public annotation class KProxyable
