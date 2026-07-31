package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== KProxyable Native Sample ===")

    val userService = KProxy.create<UserService>(userServiceHandler)

    println("--- Testing UserService ---")

    val user = userService.getUser("Native-123")
    println("Retrieved user: $user")

    val asyncUser = userService.getUserAsync("Native-456")
    println("Retrieved async user: $asyncUser")
}
