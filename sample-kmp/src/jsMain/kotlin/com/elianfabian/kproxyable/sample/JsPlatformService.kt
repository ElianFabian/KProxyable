package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface JsPlatformService {
	fun jsOnly(value: String): String
}
