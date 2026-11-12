package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.sample.probe.rawKspFunctionFacts
import kotlin.test.Test
import kotlin.test.assertEquals

/** Raw compiler observations, deliberately separate from FunctionTypeContractTest's public policy. */
class KspFunctionCharacterizationTest {
    @Test fun functionPredicatesIncludeReflectiveTypes() {
        assertEquals("true", rawKspFunctionFacts["arrow.isFunctionType"])
        assertEquals("true", rawKspFunctionFacts["explicitFunction.isFunctionType"])
        assertEquals("true", rawKspFunctionFacts["reflectedFunction.isFunctionType"])
        assertEquals("true", rawKspFunctionFacts["reflectedSuspendFunction.isSuspendFunctionType"])
        assertEquals("false", rawKspFunctionFacts["ordinaryFunction.isFunctionType"])
        assertEquals("false", rawKspFunctionFacts["lookalike.isFunctionType"])
        assertEquals("kotlin.Function1", rawKspFunctionFacts["arrow.declaration"])
        assertEquals("kotlin.reflect.KFunction1", rawKspFunctionFacts["reflectedFunction.declaration"])
        assertEquals("kotlin.reflect.KSuspendFunction1", rawKspFunctionFacts["reflectedSuspendFunction.declaration"])
    }

    @Test fun redundantProjectionsAndReceiverMarkerAreExposed() {
        assertEquals("CONTRAVARIANT,COVARIANT", rawKspFunctionFacts["redundant.variances"])
        assertEquals("true", rawKspFunctionFacts["manualReceiver.extension"])
    }

    @Test fun parameterNameLocationIsPreservedByKspBeforeNormalization() {
        for ((key, expected) in mapOf(
            "named.input" to "input", "markedReceiver.receiver" to "receiver", "markedReceiver.input" to "input",
            "nestedName.input" to "input", "nestedName.element" to "nested",
            "nestedFunction.outer" to "outer", "nestedFunction.inner" to "inner", "namedList.element" to "element",
        )) assertEquals(expected, rawKspFunctionFacts[key], key)
    }

    @Test fun highAritySymbolsHaveLogicalArgumentsWithoutContinuation() {
        assertEquals("kotlin.Function23", rawKspFunctionFacts["arity23.declaration"])
        assertEquals("kotlin.coroutines.SuspendFunction23", rawKspFunctionFacts["suspendArity23.declaration"])
        assertEquals("24", rawKspFunctionFacts["arity23.arguments"])
        assertEquals("24", rawKspFunctionFacts["suspendArity23.arguments"])
        assertEquals("true", rawKspFunctionFacts["suspendArity23.isSuspendFunctionType"])
    }
}
