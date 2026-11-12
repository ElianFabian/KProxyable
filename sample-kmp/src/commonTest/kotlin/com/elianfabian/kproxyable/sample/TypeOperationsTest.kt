package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.test.*

interface BoundParent<T> { fun <R : T> inheritedBound(value: R): R }
interface BoundMid<X> : BoundParent<List<X>>
@KProxyable interface BoundChild : BoundMid<String>

class TypeOperationsTest {
    private val t = TypeParameterId("parent", 0)
    private val u = TypeParameterId("child", 0)
    private val ref = TypeDescriptor(typeParameter = t)
    private val string = TypeDescriptor(String::class)
    private fun list(type: TypeDescriptor) = TypeDescriptor(List::class, listOf(TypeProjection.Typed(type)))

    @Test fun simultaneousSubstitutionPreservesStructureAndComposesMarks() {
        val recursive = list(ref)
        assertEquals(recursive, ref.substitute(mapOf(t to recursive)))
        val nullable = string.copy(nullability = TypeNullability.NULLABLE)
        assertEquals(nullable, ref.substitute(mapOf(t to nullable)))
        assertEquals(string, ref.copy(nullability = TypeNullability.NOT_NULL).substitute(mapOf(t to nullable)))
        val other = TypeDescriptor(typeParameter = u)
        assertEquals(other.copy(nullability = TypeNullability.NULLABLE), ref.copy(nullability = TypeNullability.NULLABLE).substitute(mapOf(t to other)))
        val callback = TypeDescriptor.function(emptyList(), TypeProjection.Typed(string), isSuspend = true)
        val replaced = list(ref).substitute(mapOf(t to callback))
        assertEquals(callback, (replaced.typeArguments.single() as TypeProjection.Typed).type)
        assertEquals(callback.copy(nullability = TypeNullability.NULLABLE), ref.copy(nullability = TypeNullability.NULLABLE).substitute(mapOf(t to callback)))
        val reflected = TypeDescriptor(kotlin.reflect.KFunction1::class, listOf(TypeProjection.Typed(ref), TypeProjection.Star))
        assertEquals(listOf(TypeProjection.Typed(string), TypeProjection.Star), reflected.substitute(mapOf(t to string)).typeArguments)
        assertNull(reflected.substitute(mapOf(t to callback)).functionFlags)
    }

    @Test fun boundsResolveGuaranteesAndNormalizationIsIdempotent() {
        val scope = mapOf(t to TypeParameterDescriptor(t, "T", upperBounds = listOf(TypeDescriptor(typeParameter = u))),
            u to TypeParameterDescriptor(u, "U", upperBounds = listOf(TypeDescriptor(Any::class))))
        assertEquals(EffectiveNullability.NOT_NULL, ref.effectiveNullability(scope))
        assertEquals(EffectiveNullability.NULLABLE, ref.copy(nullability = TypeNullability.NULLABLE).effectiveNullability(scope))
        assertEquals(EffectiveNullability.UNKNOWN, ref.effectiveNullability())
        assertEquals(EffectiveNullability.NOT_NULL, ref.copy(nullability = TypeNullability.NOT_NULL).effectiveNullability())
        val normalized = list(ref.copy(nullability = TypeNullability.NOT_NULL)).normalizeNullability(scope)
        assertEquals(list(ref), normalized)
        assertEquals(normalized, normalized.normalizeNullability(scope))
        val substituted = ref.copy(nullability = TypeNullability.NOT_NULL).substitute(mapOf(t to TypeDescriptor(typeParameter = u)))
        assertEquals(TypeNullability.UNMARKED, substituted.normalizeNullability(scope).nullability)
        val cycle = mapOf(t to TypeParameterDescriptor(t, "T", upperBounds = listOf(ref)))
        assertEquals(EffectiveNullability.UNKNOWN, ref.effectiveNullability(cycle))
        assertEquals(EffectiveNullability.UNKNOWN, ref.effectiveNullability(mapOf(t to TypeParameterDescriptor(t, "T"))))
        assertEquals(EffectiveNullability.UNKNOWN, string.copy(nullability = TypeNullability.PLATFORM).effectiveNullability())
        assertEquals(EffectiveNullability.UNKNOWN, TypeDescriptor(kind = TypeKind.DYNAMIC).effectiveNullability())
    }

    @Test fun inheritedBoundsAndScopesUseOriginalIdsAndComposedArguments() {
        var captured: FunctionDescriptor? = null
        val handler = object : ProxyHandler {
            override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? { captured = function; return args.single() }
            override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = onCall(function, args)
            override fun onGetProperty(property: PropertyDescriptor): Any? = error("unused")
            override fun onSetProperty(property: PropertyDescriptor, value: Any?) = Unit
            override fun onEquals(other: Any?): Boolean = false
            override fun onHashCode(): Int = 0
            override fun onToString(): String = "bounds"
        }
        assertEquals(listOf("ok"), TypeTestProxy.create<BoundChild>(handler).inheritedBound(listOf("ok")))
        val function = assertNotNull(captured)
        val parameter = function.typeParameters.single()
        assertEquals(list(string), parameter.upperBounds.single())
        assertEquals(parameter.id, function.returnType.typeParameter)
        assertEquals(parameter, function.typeParameterScope()[parameter.id])
        assertEquals(function, function.normalizeNullability())
        val manual = FunctionDescriptor("echo", ref, typeParameters = listOf(TypeParameterDescriptor(u, "T", upperBounds = listOf(ref))),
            owner = InterfaceDescriptor(BoundParent::class, listOf(TypeParameterDescriptor(t, "T"))))
        val changed = manual.substitute(mapOf(t to string))
        assertEquals(string, changed.typeParameters.single().upperBounds.single())
        assertEquals(u, changed.typeParameters.single().id)
        assertEquals(2, changed.typeParameterScope().size)
        val property = PropertyDescriptor("name", ref, owner = manual.owner)
        assertEquals(string, property.substitute(mapOf(t to string)).type)
        assertEquals(1, property.typeParameterScope().size)
    }
}
