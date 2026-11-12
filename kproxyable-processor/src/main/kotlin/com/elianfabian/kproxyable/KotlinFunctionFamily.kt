package com.elianfabian.kproxyable

/** The ordinary and suspend builtin families; reflection types deliberately remain classifiers. */
internal enum class KotlinFunctionFamily {
    /** Built-in kotlin.FunctionN declarations, including arities above 22. */
    REGULAR,

    /** Built-in kotlin.coroutines.SuspendFunctionN declarations with logical, source-level arguments. */
    SUSPEND,
}

private val regularFunctionName = Regex("kotlin\\.Function(0|[1-9][0-9]*)")
private val suspendFunctionName = Regex("kotlin\\.coroutines\\.SuspendFunction(0|[1-9][0-9]*)")

/** Matches KSP declaration names at compile time, never names obtained from runtime KClass tokens. */
internal fun kotlinFunctionFamily(qualifiedName: String?): KotlinFunctionFamily? = when {
    qualifiedName == null -> null
    regularFunctionName.matches(qualifiedName) -> KotlinFunctionFamily.REGULAR
    suspendFunctionName.matches(qualifiedName) -> KotlinFunctionFamily.SUSPEND
    else -> null
}
