package com.elianfabian.kproxyable

import kotlin.reflect.KClass

/**
 * Smart wrapper for proxy arguments that applies safe casting automatically
 * based on the function descriptor. This makes "args[0] as Int" work in WasmJs.
 */
public class KProxyArgs(
    private val values: List<Any?>,
    private val descriptor: FunctionDescriptor
) : List<Any?> by values {
    
    override fun get(index: Int): Any? {
        val value = values[index]
        val targetType = descriptor.parameters.getOrNull(index)?.type?.classifier ?: return value
        return value.kproxySafeCast(targetType)
    }

    override fun iterator(): Iterator<Any?> = object : Iterator<Any?> {
        private var index = 0
        override fun hasNext(): Boolean = index < size
        override fun next(): Any? = get(index++)
    }
    
    override fun subList(fromIndex: Int, toIndex: Int): List<Any?> = values.subList(fromIndex, toIndex)
}

/**
 * Internal helper used by generated code to safely cast values.
 * Handles the Number -> Primitive conversion critical for WasmJs/JS.
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Any> Any?.kproxySafeCast(target: KClass<T>): T? {
    if (this == null) return null
    
    // Fast path: Exact match
    if (target.isInstance(this)) return this as T

    // Platform path: Handle Number conversions for JS/Wasm
    if (this is Number) {
        return when (target) {
            Int::class -> this.toInt() as T
            Long::class -> this.toLong() as T
            Float::class -> this.toFloat() as T
            Double::class -> this.toDouble() as T
            Byte::class -> this.toByte() as T
            Short::class -> this.toShort() as T
            else -> this as T
        }
    }

    return this as T
}
