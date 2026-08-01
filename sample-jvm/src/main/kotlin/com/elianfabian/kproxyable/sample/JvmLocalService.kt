package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface JvmLocalService {
    fun jvmSpecificAction(input: Int): Int
}
