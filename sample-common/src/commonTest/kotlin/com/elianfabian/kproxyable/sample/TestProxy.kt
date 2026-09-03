package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyFactory
import com.elianfabian.kproxyable.KProxyRegistry
import com.elianfabian.kproxyable.KProxyable
import com.elianfabian.kproxyable.create
import kotlin.test.Test
import kotlin.test.assertEquals

@KProxyable
interface MyTestService {
	fun greet(value: String): String
}

@KProxyRegistry
@Suppress("KotlinNoActualForExpect")
expect object TestProxy : KProxyFactory

class ProxyTest {
	@Test
	fun testProxyGeneration() {
		val service = TestProxy.create<MyTestService>(DemoHandler())
		val result = service.greet("World")
		assertEquals("Hello from DemoHandler, World", result)
	}
}
