package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*

//suspend fun main() {
//	val loggingHandler = object : ProxyHandler {
//		override fun onCall(function: FunctionDescriptor, args: Array<Any?>): Any? {
//			println("▶ [onCall: ${function.returnType.classifier}] ${function.name}(${args.joinToString()})")
//
//			if (function.returnType.classifier == Boolean::class) {
//				return true
//			}
//			if (function.returnType.classifier == Unit::class) {
//				return Unit
//			}
//			if (function.returnType.isNullable) {
//				return null
//			}
//
//			throw NotImplementedError("Return type ${function.returnType.classifier} is not handled in onCall")
//		}
//
//		override suspend fun onSuspendCall(function: FunctionDescriptor, args: Array<Any?>): Any? {
//			println("▶ [onSuspendCall: ${function.returnType.classifier}] ${function.name}(${args.joinToString()})")
//
//			if (function.returnType.classifier == Boolean::class) {
//				return true
//			}
//			if (function.returnType.classifier == Unit::class) {
//				return Unit
//			}
//			if (function.returnType.isNullable) {
//				return null
//			}
//
//			return null
//		}
//
//		override fun onGetProperty(property: PropertyDescriptor): Any? {
//			println("▶ [onGetProperty] Reading '${property.name}'")
//			return when (property.name) {
//				"isLoggingSupported" -> true
//				"isLoggingEnabled" -> false
//				else -> null
//			}
//		}
//
//		override fun onSetProperty(property: PropertyDescriptor, value: Any?) {
//			println("▶ [onSetProperty] Setting '${property.name}' to $value")
//		}
//
//		override fun onEquals(other: Any?): Boolean {
//			println("▶ [equals] Comparing with $other")
//			return this === other
//		}
//
//		override fun onHashCode(): Int {
//			println("▶ [hashCode] Calculating hash code")
//			return System.identityHashCode(this)
//		}
//
//		override fun onToString(): String {
//			println("▶ [toString] Converting to string")
//			return "LoggingHandler"
//		}
//	}
//
//
//
//	// Create the proxy instance
//	//val myInterface = KProxy.create<MyInterface>(loggingHandler)
//	// We have to use an IR plugin like Ktorfit uses to replace the KProxy.create call with the generated proxy class
//	val myInterface = KProxy.create<MyInterface>(loggingHandler)
//
//	println("--- Testing KProxyable ---")
//
//	// 1. Regular method call
//	myInterface.log("Hello from KProxyable!")
//
//	// 2. Suspend method call
//	myInterface.suspendLog("Async log message")
//
//	// 3. Property getter
//	val supported = myInterface.isLoggingSupported
//	println("Result from getter -> isLoggingSupported: $supported")
//
//	// 4. Property setter
//	myInterface.isLoggingEnabled = true
//}

suspend fun main() {
	println("=== KProxyable JVM Sample ===")

	val userService = KProxy.create<UserService>(userServiceHandler)

	println("--- Testing UserService ---")

	val user = userService.getUser("123")
	println("Retrieved user: $user")

	val asyncUser = userService.getUserAsync("456")
	println("Retrieved async user: $asyncUser")
}
