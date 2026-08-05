package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface NativePlatformService {
    fun nativeOnly(value: String): String
}
