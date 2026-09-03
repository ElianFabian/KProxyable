package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyFactory
import com.elianfabian.kproxyable.KProxyRegistry

@Suppress("KotlinNoActualForExpect")
@KProxyRegistry
expect object KProxy : KProxyFactory
