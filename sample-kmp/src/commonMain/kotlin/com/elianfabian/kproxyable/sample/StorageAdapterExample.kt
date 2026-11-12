package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*

/** A domain identifier whose stored representation is supplied by an adapter. */
data class StoredUserId(val value: String)
data class StoredUser(val id: StoredUserId, val name: String)
data class UserRow(val id: String, val name: String)

/** Storage conversion is owned by this example library, not by the proxy runtime. */
interface ColumnAdapter<T> {
    fun encode(value: T): String
    fun decode(value: String): T
}

/** Configuration can be injected without reflection or a no-argument constructor. */
class UserIdColumnAdapter(private val prefix: String) : ColumnAdapter<StoredUserId> {
    override fun encode(value: StoredUserId): String = prefix + value.value
    override fun decode(value: String): StoredUserId {
        require(value.startsWith(prefix)) { "Invalid stored user id" }
        return StoredUserId(value.removePrefix(prefix))
    }
}

interface UserRows { fun find(id: String): UserRow? }

@KProxyable
interface AdaptedUserStore { fun findById(id: StoredUserId): StoredUser? }

/** Executable adapter example: KProxyable generates dispatch; this library maps rows and values. */
class UserStoreHandler(private val rows: UserRows, private val adapter: ColumnAdapter<StoredUserId>) : ProxyHandler {
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        require(function.name == "findById" && function.owner.classifier == AdaptedUserStore::class)
        val key = adapter.encode(args.single() as StoredUserId)
        val row = rows.find(key) ?: return null
        return StoredUser(adapter.decode(row.id), row.name)
    }
    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = onCall(function, args)
    override fun onGetProperty(property: PropertyDescriptor): Any? = error("No properties")
    override fun onSetProperty(property: PropertyDescriptor, value: Any?): Unit = error("No properties")
    override fun onEquals(other: Any?): Boolean = this === other
    override fun onHashCode(): Int = rows.hashCode()
    override fun onToString(): String = "UserStore"
}
