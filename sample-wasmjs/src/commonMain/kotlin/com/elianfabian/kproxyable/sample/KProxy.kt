package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyFactory
import com.elianfabian.kproxyable.KProxyRegistry

@KProxyRegistry
@Suppress("KotlinNoActualForExpect")
expect object KProxy : KProxyFactory
