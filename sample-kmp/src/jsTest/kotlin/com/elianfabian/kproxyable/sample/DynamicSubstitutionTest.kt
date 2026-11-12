package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.test.*

private fun <T> nullableIdentity(value: T): T? = value
private fun <T> definitelyIdentity(value: T & Any): T & Any = value

class DynamicSubstitutionTest {
    @Test fun dynamicReplacementRetainsDynamicSemanticsWithOuterMarks() {
        val nullable: dynamic = nullableIdentity<dynamic>(null)
        assertNull(nullable)
        val definite: dynamic = definitelyIdentity<dynamic>("ok")
        assertEquals("ok", definite as String)
        val id = TypeParameterId("test", 0)
        val dynamicType = TypeDescriptor(kind = TypeKind.DYNAMIC)
        for (mark in TypeNullability.entries) {
            assertEquals(dynamicType, TypeDescriptor(typeParameter = id, nullability = mark).substitute(mapOf(id to dynamicType)))
        }
    }
}
