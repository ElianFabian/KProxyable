package com.elianfabian.kproxyable

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Declaration-name policy; raw KSP observations are recorded in docs/FUNCTION_TYPE_PROBE.md. */
class KotlinFunctionFamilyTest {
    @Test
    fun recognizesBothBuiltInFamiliesWithoutAnArityLimit() {
        for (arity in listOf(0, 1, 22, 23, 100)) {
            assertEquals(KotlinFunctionFamily.REGULAR, kotlinFunctionFamily("kotlin.Function$arity"))
            assertEquals(KotlinFunctionFamily.SUSPEND, kotlinFunctionFamily("kotlin.coroutines.SuspendFunction$arity"))
        }
    }

    @Test
    fun excludesReflectionSupertypesAndLookalikes() {
        for (name in listOf(
            null, "kotlin.Function", "kotlin.FunctionN", "kotlin.Function01",
            "kotlin.reflect.KFunction1", "kotlin.reflect.KSuspendFunction1",
            "example.Function1", "example.kotlin.Function1", "kotlin.Function1Extra",
            "kotlin.coroutines.SuspendFunction1.Extra", "kotlin.coroutines.SuspendFunction",
        )) {
            assertNull(kotlinFunctionFamily(name), name)
        }
    }
}
