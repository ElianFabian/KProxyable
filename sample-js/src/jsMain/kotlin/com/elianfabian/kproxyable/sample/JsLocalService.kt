package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
public interface JsLocalService {
	public fun greet(name: String): String
}
