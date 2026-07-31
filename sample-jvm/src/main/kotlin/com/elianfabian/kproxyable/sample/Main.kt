package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlinx.coroutines.runBlocking

@KProxyable
interface JvmService {
    fun hello(name: String): String
}

val jvmHandler = object : ProxyHandler {
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        if (function.name == "hello") {
            return "Hello, ${args[0]} from JVM Reflection!"
        }
        return null
    }

    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = null
    override fun onGetProperty(property: PropertyDescriptor): Any? = null
    override fun onSetProperty(property: PropertyDescriptor, value: Any?) {}
    override fun onEquals(other: Any?): Boolean = this === other
    override fun onHashCode(): Int = 0
    override fun onToString(): String = "JvmHandler"
}

fun main() = runBlocking {
    println("=== KProxyable JVM Sample (Reflection) ===")
    
    // Note: No manual 'expect/actual' object needed here!
    val service = KProxy.create<JvmService>(jvmHandler)
    
    println(service.hello("Developer"))
}
