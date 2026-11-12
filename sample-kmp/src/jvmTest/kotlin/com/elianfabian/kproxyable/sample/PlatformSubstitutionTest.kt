package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.test.*

private fun <T> definitelyIdentity(value: T & Any): T & Any = value

class PlatformSubstitutionTest {
    @Test fun definitelyNonNullProjectionOfJavaPlatformValueCompilesAsString() {
        val value: String = definitelyIdentity(System.getProperty("java.version"))
        assertTrue(value.isNotEmpty())
        val id = TypeParameterId("platform", 0)
        val platform = TypeDescriptor(String::class, nullability = TypeNullability.PLATFORM)
        assertEquals(TypeNullability.NOT_NULL,
            TypeDescriptor(typeParameter = id, nullability = TypeNullability.NOT_NULL).substitute(mapOf(id to platform)).nullability)
        assertEquals(platform, TypeDescriptor(typeParameter = id).substitute(mapOf(id to platform)))
    }
}
