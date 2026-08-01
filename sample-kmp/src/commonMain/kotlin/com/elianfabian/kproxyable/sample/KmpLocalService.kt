package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface KmpLocalService {
    fun kmpSpecificAction(input: String): String
}
