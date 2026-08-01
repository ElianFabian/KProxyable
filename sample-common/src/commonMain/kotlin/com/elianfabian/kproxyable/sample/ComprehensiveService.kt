package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface ComprehensiveService {
    fun performAction(id: Int, payload: String): Boolean
    suspend fun fetchDataAsync(query: String): List<String>
    val version: String
    var isActive: Boolean
}
