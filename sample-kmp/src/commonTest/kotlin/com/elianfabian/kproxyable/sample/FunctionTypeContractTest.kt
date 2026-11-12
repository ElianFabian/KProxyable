package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.reflect.KVariance
import kotlin.reflect.KFunction1
import kotlin.reflect.KSuspendFunction1
import kotlin.test.*
import kotlinx.coroutines.test.runTest

typealias ProbeFunctionAlias = (Int) -> String
typealias GenericHandler<T> = (T) -> Unit
typealias SuspendHandler<T> = suspend (T) -> String

interface ReflectionParent<T> {
    fun inheritedReflection(): KFunction1<T, String>?
}

/** End-to-end public contract; historical raw KSP observations are in docs/FUNCTION_TYPE_PROBE.md. */
@KProxyable
@Suppress("REDUNDANT_PROJECTION")
interface FunctionTypeProbe : ReflectionParent<Int> {
    fun ordinaryFunction(): Function<Int>?
    fun reflectedFunction(): KFunction1<Int, String>?
    fun reflectedSuspendFunction(): KSuspendFunction1<Int, String>?
    fun <T : () -> Unit> boundedFunction(value: T)
    fun alias(value: ProbeFunctionAlias)
    fun arrow(value: (Int) -> String)
    fun explicitFunction(value: Function1<Int, String>)
    fun nestedFunction(value: (outer: Int) -> (inner: Int) -> Unit)
    fun namedList(value: List<@ParameterName("element") Int>)
    fun redundant(value: Function1<in Int, out String>)
    fun manualReceiver(value: @ExtensionFunctionType Function2<String, Int, Boolean>)
    fun named(value: (input: Int) -> String)
    fun markedReceiver(value: (@ParameterName("receiver") String).(input: Int) -> String)
    fun nestedName(value: (input: List<@ParameterName("nested") Int>) -> String)
    fun genericInt(value: GenericHandler<Int>)
    fun genericString(value: GenericHandler<String>)
    fun nullableAlias(value: GenericHandler<Int>?)
    fun suspendAlias(value: SuspendHandler<Int>)
    fun plainSuspend(value: suspend (Int) -> String)
    fun suspendReceiver(value: suspend String.(Int) -> Boolean)
    fun nestedSuspend(value: (suspend () -> Unit) -> Unit)
    fun nullableFunction(value: ((Int) -> String)?)
    fun projectedFunction(value: Function1<*, String>)
    fun unknownFunction(): Function1<*, *>?
    fun lookalike(value: com.elianfabian.kproxyable.sample.lookalike.Function1<Int, String>)
    fun arity22(): ((Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> String)?
    fun arity23(): ((Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> String)?
    fun suspendArity23(): (suspend (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> String)?
}

private class ProbeHandler : ProxyHandler {
    lateinit var function: FunctionDescriptor
    var result: Any? = Unit
    var arguments: List<Any?> = emptyList()
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        this.function = function
        arguments = args
        return result
    }
    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = onCall(function, args)
    override fun onGetProperty(property: PropertyDescriptor): Any? = error("Not used by this probe")
    override fun onSetProperty(property: PropertyDescriptor, value: Any?) = error("Not used by this probe")
    override fun onEquals(other: Any?): Boolean = false
    override fun onHashCode(): Int = 0
    override fun onToString(): String = "probe"
}

class FunctionTypeContractTest {
    private val handler = ProbeHandler()
    private val proxy = TypeTestProxy.create<FunctionTypeProbe>(handler)

    @Test fun redundantProjectionsAreCanonical() {
        proxy.redundant { it.toString() }
        val type = handler.function.parameters.single().type
        assertEquals(listOf(KVariance.INVARIANT, KVariance.INVARIANT), type.typeArguments.map { (it as TypeProjection.Typed).variance })
        assertNotNull(type.functionType)
        proxy.arrow { it.toString() }
        assertEquals(type, handler.function.parameters.single().type)
    }

    @Test fun manuallyAnnotatedFunctionHasAReceiver() {
        proxy.manualReceiver { _ -> true }
        val type = handler.function.parameters.single().type
        val signature = assertNotNull(type.functionType)
        assertEquals(String::class, signature.receiverType!!.classifier)
        assertEquals(Int::class, signature.parameters.single().classifier)
        assertEquals(Boolean::class, signature.returnType.classifier)
        assertEquals(FunctionTypeFlags(hasReceiver = true), type.functionFlags)
        assertTrue(type.annotations.none { it is ExtensionFunctionType })
    }

    @Test fun compilerAnnotationsAreRemovedOnlyFromDirectInputs() {
        proxy.named { it.toString() }
        val ordinary = assertNotNull(handler.function.parameters.single().type.functionType)
        assertTrue(ordinary.parameters.single().annotations.none { it is ParameterName })
        proxy.markedReceiver { it.toString() }
        val extension = assertNotNull(handler.function.parameters.single().type.functionType)
        assertTrue(extension.receiverType!!.annotations.none { it is ParameterName })
        assertTrue(extension.parameters.single().annotations.none { it is ParameterName })
        proxy.nestedName { it.size.toString() }
        val nested = assertNotNull(handler.function.parameters.single().type.functionType).parameters.single()
        assertTrue(nested.annotations.none { it is ParameterName })
        assertEquals("nested", (nested.typeArguments.single() as TypeProjection.Typed).type.annotations.filterIsInstance<ParameterName>().single().name)
    }

    @Test fun reflectionAndFunctionSupertypeKeepTheirOrdinaryIdentity() {
        handler.result = null
        assertNull(proxy.ordinaryFunction())
        assertEquals(Function::class, handler.function.returnType.classifier)
        assertNull(handler.function.returnType.functionType)
        assertNull(handler.function.returnType.functionFlags)
        assertEquals(1, handler.function.returnType.typeArguments.size)
        assertNull(proxy.reflectedFunction())
        assertEquals(KFunction1::class, handler.function.returnType.classifier)
        assertNull(handler.function.returnType.functionType)
        assertNull(handler.function.returnType.functionFlags)
        assertEquals(2, handler.function.returnType.typeArguments.size)
        assertNull(proxy.reflectedSuspendFunction())
        assertEquals(KSuspendFunction1::class, handler.function.returnType.classifier)
        assertNull(handler.function.returnType.functionType)
        assertNull(handler.function.returnType.functionFlags)
        assertEquals(2, handler.function.returnType.typeArguments.size)
        assertNull(proxy.inheritedReflection())
        val inherited = handler.function.returnType
        assertEquals(KFunction1::class, inherited.classifier)
        assertNull(inherited.functionFlags)
        assertNull(inherited.functionType)
        assertEquals(Int::class, (inherited.typeArguments.first() as TypeProjection.Typed).type.classifier)
        handler.result = Unit
        proxy.boundedFunction {}
        assertEquals(TypeKind.PARAMETER, handler.function.parameters.single().type.kind)
        assertNull(handler.function.parameters.single().type.functionType)
        assertNull(handler.function.parameters.single().type.functionFlags)
        assertNotNull(handler.function.typeParameters.single().upperBounds.single().functionType)
    }

    @Test fun aliasArrowExplicitFunctionAndFactoryAreIdentical() {
        proxy.alias { it.toString() }
        val alias = handler.function.parameters.single().type
        proxy.arrow { it.toString() }
        val arrow = handler.function.parameters.single().type
        proxy.explicitFunction { it.toString() }
        val explicit = handler.function.parameters.single().type
        assertNotNull(alias.functionType)
        assertEquals(alias, arrow)
        assertEquals(arrow, explicit)
        assertEquals(Function::class, arrow.classifier)
        assertEquals(FunctionTypeFlags(), arrow.functionFlags)
        assertEquals(TypeDescriptor.function(
            parameters = listOf(TypeProjection.Typed(TypeDescriptor(Int::class))),
            returnType = TypeProjection.Typed(TypeDescriptor(String::class)),
        ), arrow)
    }

    @Test fun parameterNamesAreRemovedAtEveryFunctionNode() {
        proxy.nestedFunction { _ -> { _ -> Unit } }
        val outer = assertNotNull(handler.function.parameters.single().type.functionType)
        assertTrue(outer.parameters.single().annotations.none { it is ParameterName })
        val inner = assertNotNull(outer.returnType.functionType)
        assertTrue(inner.parameters.single().annotations.none { it is ParameterName })
        proxy.namedList(emptyList())
        val list = handler.function.parameters.single().type
        assertNull(list.functionType)
        val element = (list.typeArguments.single() as TypeProjection.Typed).type
        assertEquals("element", element.annotations.filterIsInstance<ParameterName>().single().name)
    }

    @Test fun suspendArity23AlsoHasOnlyLogicalArguments() {
        handler.result = null
        assertNull(proxy.suspendArity23())
        val type = handler.function.returnType
        assertEquals(24, type.typeArguments.size)
        val signature = assertNotNull(type.functionType)
        assertTrue(signature.isSuspend)
        assertEquals(23, signature.parameters.size)
        assertTrue(signature.parameters.all { it.classifier == Int::class })
        assertEquals(String::class, signature.returnType.classifier)
        assertEquals(Function::class, type.classifier)
        assertEquals(FunctionTypeFlags(isSuspend = true), type.functionFlags)
    }

    @Test fun highArityHasOnlyLogicalArguments() {
        handler.result = null
        assertNull(proxy.arity22())
        val type22 = handler.function.returnType
        assertEquals(23, type22.typeArguments.size)
        assertEquals(22, assertNotNull(type22.functionType).parameters.size)
        assertNull(proxy.arity23())
        val type23 = handler.function.returnType
        assertEquals(24, type23.typeArguments.size)
        assertEquals(23, assertNotNull(type23.functionType).parameters.size)
        assertEquals(String::class, type23.functionType!!.returnType.classifier)
        assertEquals(Function::class, type22.classifier)
        assertEquals(Function::class, type23.classifier)
        assertEquals(FunctionTypeFlags(), type23.functionFlags)
    }

    @Test fun genericSuspendAndNullableAliasesPreserveTheirPositions() {
        proxy.genericInt {}
        val intHandler = handler.function.parameters.single().type
        assertEquals(Int::class, intHandler.functionType!!.parameters.single().classifier)
        assertEquals(Unit::class, intHandler.functionType!!.returnType.classifier)
        proxy.genericString {}
        assertEquals(String::class, handler.function.parameters.single().type.functionType!!.parameters.single().classifier)
        proxy.nullableAlias(null)
        assertEquals(intHandler.copy(nullability = TypeNullability.NULLABLE), handler.function.parameters.single().type)
        proxy.suspendAlias { it.toString() }
        val alias = handler.function.parameters.single().type
        assertEquals(FunctionTypeFlags(isSuspend = true), alias.functionFlags)
        proxy.plainSuspend { it.toString() }
        assertEquals(alias, handler.function.parameters.single().type)
    }

    @Test fun nullableNestedAndStarProjectedFunctionsRetainFlags() {
        proxy.arrow { it.toString() }
        val plain = handler.function.parameters.single().type
        proxy.nullableFunction(null)
        assertEquals(plain.copy(nullability = TypeNullability.NULLABLE), handler.function.parameters.single().type)
        proxy.nestedSuspend {}
        val nested = handler.function.parameters.single().type.functionType!!.parameters.single()
        assertEquals(FunctionTypeFlags(isSuspend = true), nested.functionFlags)
        assertEquals(Unit::class, nested.functionType!!.returnType.classifier)
        val callback: (Int) -> String = { it.toString() }
        proxy.projectedFunction(callback)
        val projected = handler.function.parameters.single().type
        assertEquals(FunctionTypeFlags(), projected.functionFlags)
        assertEquals(TypeProjection.Star, projected.typeArguments.first())
        assertNull(projected.functionType)
        handler.result = null
        assertNull(proxy.unknownFunction())
        val unknown = handler.function.returnType
        assertEquals(listOf(TypeProjection.Star, TypeProjection.Star), unknown.typeArguments)
        assertEquals(FunctionTypeFlags(), unknown.functionFlags)
        assertNull(unknown.functionType)
    }

    @Test fun aUserClassNamedFunction1IsAnOrdinaryClassifier() {
        proxy.lookalike(com.elianfabian.kproxyable.sample.lookalike.Function1())
        val type = handler.function.parameters.single().type
        assertEquals(com.elianfabian.kproxyable.sample.lookalike.Function1::class, type.classifier)
        assertNull(type.functionFlags)
        assertNull(type.functionType)
        assertEquals(2, type.typeArguments.size)
    }

    @Test fun handlerReceivesTheOriginalSuspendCallback() = runTest {
        val callback: suspend String.(Int) -> Boolean = { length > it }
        proxy.suspendReceiver(callback)
        val type = handler.function.parameters.single().type
        assertEquals(FunctionTypeFlags(isSuspend = true, hasReceiver = true), type.functionFlags)
        assertEquals(3, type.typeArguments.size)
        assertEquals(Function::class, type.classifier)
        val actual = handler.arguments.single()
        assertSame(callback, actual)
        @Suppress("UNCHECKED_CAST")
        val typed = actual as suspend String.(Int) -> Boolean
        assertTrue(typed("abcd", 2))
        assertFalse(typed("a", 2))
    }
}
