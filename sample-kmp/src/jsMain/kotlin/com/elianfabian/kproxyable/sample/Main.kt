package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create

// Execute the task jsNodeRun
suspend fun main() {
	println("=== KProxyable JS Sample ===")

	val userService = KProxy.create<UserService>(userServiceHandler)

	println("--- Testing UserService ---")

	val user = userService.getUser("123")
	println("Retrieved user: $user")

	val asyncUser = userService.getUserAsync("456")
	println("Retrieved async user: $asyncUser")
}
