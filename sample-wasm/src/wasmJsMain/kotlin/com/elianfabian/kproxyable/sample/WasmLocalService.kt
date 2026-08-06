package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface WasmLocalService {
    fun wasmOnly(value: String): String
}
