package com.elianfabian.kproxyable

@Target(AnnotationTarget.CLASS)
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
