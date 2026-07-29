package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.FunctionDescriptor
import com.elianfabian.kproxyable.KProxy
import com.elianfabian.kproxyable.KProxyable
import com.elianfabian.kproxyable.PropertyDescriptor
import com.elianfabian.kproxyable.ProxyHandler

// TODO: see how to execute this code on Kotlin/JS
suspend fun main() {
	println("=== KProxyable JS Sample ===")

	val userService = KProxy.create<UserService>(userServiceHandler)

	println("--- Testing UserService ---")

	val user = userService.getUser("123")
	println("Retrieved user: $user")

	val asyncUser = userService.getUserAsync("456")
	println("Retrieved async user: $asyncUser")
}
