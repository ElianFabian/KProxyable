package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface JvmPlatformService {
	fun jvmOnly(value: String): String
}
