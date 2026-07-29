package com.elianfabian.kproxyable

/**
 * Marks an interface to enable compile-time proxy generation with `KProxyable`.
 *
 * When applied directly to an interface, the KSP processor generates a proxy implementation
 * that routes function calls, property accesses, and standard [Any] methods to a [ProxyHandler].
 *
 * Can also be used as a **meta-annotation** on custom annotations (e.g., `@HttpClient`) to automatically
 * trigger proxy generation for any interface annotated with those custom annotations.
 *
 * @property lazyDescriptors Defines the initialization behavior for function and property descriptors.
 * Defaults to [LazyDescriptorsMode.AUTO].
 */
@Target(
	AnnotationTarget.CLASS,
	AnnotationTarget.ANNOTATION_CLASS,
)
@Retention(AnnotationRetention.SOURCE)
public annotation class KProxyable(
	val lazyDescriptors: LazyDescriptorsMode = LazyDescriptorsMode.AUTO
)

public enum class LazyDescriptorsMode {

	/**
	 * KSP decides based on member count (>= 20 members -> lazy).
	 */
	AUTO,

	/**
	 * Always generate 'by lazy'.
	 */
	ALWAYS,

	/**
	 * Always generate direct eager properties.
	 */
	NEVER,
}
