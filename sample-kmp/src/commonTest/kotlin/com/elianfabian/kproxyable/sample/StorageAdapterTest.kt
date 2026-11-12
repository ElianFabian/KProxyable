package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.create
import kotlin.test.*

class StorageAdapterTest {
    @Test fun findByIdConvertsBothDirectionsWithInjectedDependencies() {
        var requested: String? = null
        val rows = object : UserRows {
            override fun find(id: String): UserRow { requested = id; return UserRow("user:42", "Ada") }
        }
        val store = TypeTestProxy.create<AdaptedUserStore>(UserStoreHandler(rows, UserIdColumnAdapter("user:")))
        assertEquals(StoredUser(StoredUserId("42"), "Ada"), store.findById(StoredUserId("7")))
        assertEquals("user:7", requested)
    }

    @Test fun missingRowsAndConversionFailuresPropagate() {
        val missing = object : UserRows { override fun find(id: String): UserRow? = null }
        val adapter = UserIdColumnAdapter("user:")
        assertNull(TypeTestProxy.create<AdaptedUserStore>(UserStoreHandler(missing, adapter)).findById(StoredUserId("7")))
        val invalid = object : UserRows { override fun find(id: String): UserRow = UserRow("invalid", "Ada") }
        assertFailsWith<IllegalArgumentException> {
            TypeTestProxy.create<AdaptedUserStore>(UserStoreHandler(invalid, adapter)).findById(StoredUserId("7"))
        }
    }
}
