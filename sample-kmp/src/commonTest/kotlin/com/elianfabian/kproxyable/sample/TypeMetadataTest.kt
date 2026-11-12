package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.jvm.JvmInline
import kotlin.reflect.KVariance
import kotlin.test.*
import kotlinx.coroutines.test.runTest

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.BINARY)
annotation class MetadataTag(val value: String = "default")

/** Enum argument used to verify generated annotation constructor expressions. */
enum class MetadataMode {
    /** The fixture's default mode; its identity must survive generation on every target. */
    FIRST,
}
annotation class NestedMetadata(val value: String)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class ComplexMetadata(
    val nested: NestedMetadata = NestedMetadata("nested"),
    val mode: MetadataMode = MetadataMode.FIRST,
    val numbers: IntArray = intArrayOf(1, 2),
    val target: kotlin.reflect.KClass<*> = String::class,
)
@JvmInline
value class MetadataId(val value: String)

typealias Names<T> = List<T?>
typealias UserNames = List<String?>

interface GenericParent<T> {
    fun inherited(value: T): List<T?>
    var inheritedProperty: T
}

@KProxyRegistry
expect object TypeTestProxy : KProxyFactory

@KProxyable
interface TypeService : GenericParent<String> {
    @MetadataTag("function")
    fun projections(@MetadataTag("argument") value: Map<in String, *>): Array<out Number>
    @ComplexMetadata
    fun complex(value: MetadataId, array: IntArray): Unit
    fun String.extension(value: Int): String
    fun aliasGeneric(): Names<String>
    fun nested(value: List<List<String?>?>): UserNames
    fun callback(value: suspend String.(Int) -> Boolean): Unit
    fun annotated(value: List<@MetadataTag("type") String>): Unit
    fun <T> bounded(value: T): T where T : CharSequence, T : Comparable<T>
    fun <T> definitely(value: T & Any): T & Any
    fun <T> nullable(value: T?): T?
    suspend fun suspended(): String
    fun overloaded(value: Int): String
    fun overloaded(value: String): String
    fun varargs(vararg values: String): Unit
}

@KProxyable
interface GenericService<T> : GenericParent<T> where T : CharSequence, T : Comparable<T> {
    fun echo(value: T): T
    fun <T> shadow(value: T): T
}

@KProxyable
interface CovariantService<out T> { fun value(): T }
@KProxyable
interface ContravariantService<in T> { fun consume(value: T) }

private class RecordingHandler : ProxyHandler {
    lateinit var function: FunctionDescriptor
    lateinit var property: PropertyDescriptor
    var arguments: List<Any?> = emptyList()
    var result: Any? = Unit
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        this.function = function
        assertEquals(function, function.normalizeNullability())
        arguments = args
        return result
    }
    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = onCall(function, args)
    override fun onGetProperty(property: PropertyDescriptor): Any? { this.property = property; return "property" }
    override fun onSetProperty(property: PropertyDescriptor, value: Any?) { this.property = property; result = value }
    override fun onEquals(other: Any?): Boolean = false
    override fun onHashCode(): Int = 42
    override fun onToString(): String = "recording"
}

class TypeMetadataTest {
    private val handler = RecordingHandler()
    private val proxy = TypeTestProxy.create<TypeService>(handler)

    @Test fun projectionsAndAnnotationsSurviveGeneration() {
        handler.result = arrayOf<Number>(1)
        proxy.projections(mapOf("x" to 1))
        val parameter = handler.function.parameters.single()
        val arguments = parameter.type.typeArguments
        assertEquals(KVariance.IN, (arguments[0] as TypeProjection.Typed).variance)
        assertEquals(String::class, (arguments[0] as TypeProjection.Typed).type.classifier)
        assertEquals(TypeProjection.Star, arguments[1])
        assertEquals(KVariance.OUT, (handler.function.returnType.typeArguments.single() as TypeProjection.Typed).variance)
        assertEquals("function", handler.function.annotations.filterIsInstance<MetadataTag>().single().value)
        assertEquals("argument", parameter.annotations.filterIsInstance<MetadataTag>().single().value)
    }

    @Test fun nestedNullabilityAndAliases() {
        handler.result = listOf("ok", null)
        assertEquals(listOf("ok", null), proxy.nested(listOf(null)))
        val inner = (handler.function.parameters.single().type.typeArguments.single() as TypeProjection.Typed).type
        assertEquals(TypeNullability.NULLABLE, inner.nullability)
        assertEquals(TypeNullability.NULLABLE, (inner.typeArguments.single() as TypeProjection.Typed).type.nullability)
        assertEquals(List::class, handler.function.returnType.classifier)
        proxy.aliasGeneric()
        assertEquals(TypeNullability.NULLABLE, (handler.function.returnType.typeArguments.single() as TypeProjection.Typed).type.nullability)
    }

    @Test fun suspendFunctionTypeAndTypeAnnotations() {
        proxy.callback { length > it }
        val shape = assertNotNull(handler.function.parameters.single().type.functionType)
        assertTrue(shape.isSuspend)
        assertEquals(String::class, shape.receiverType?.classifier)
        assertEquals(Int::class, shape.parameters.single().classifier)
        assertEquals(Boolean::class, shape.returnType.classifier)
        proxy.annotated(emptyList())
        val element = (handler.function.parameters.single().type.typeArguments.single() as TypeProjection.Typed).type
        assertEquals("type", element.annotations.filterIsInstance<MetadataTag>().single().value)
    }

    @Test fun recursiveBoundsAndParameterIdentity() {
        handler.result = "ok"
        assertEquals("ok", proxy.bounded("ok"))
        val parameter = handler.function.typeParameters.single()
        assertEquals(2, parameter.upperBounds.size)
        val comparable = parameter.upperBounds.single { it.classifier == Comparable::class }
        assertEquals(parameter.id, (comparable.typeArguments.single() as TypeProjection.Typed).type.typeParameter)
        assertEquals(parameter.id, handler.function.returnType.typeParameter)
        assertEquals(TypeNullability.UNMARKED, handler.function.returnType.nullability)
        proxy.definitely("ok")
        assertNotNull(handler.function.returnType.typeParameter)
        assertEquals(TypeService::class, handler.function.owner.classifier)
        assertEquals(TypeNullability.NOT_NULL, handler.function.returnType.nullability)
        proxy.nullable("ok")
        assertNotEquals(parameter.id, handler.function.typeParameters.single().id)
        assertEquals(TypeNullability.NULLABLE, handler.function.returnType.nullability)
    }

    @Test fun inheritedMembersOverloadsAndVarargs() {
        handler.result = listOf("ok")
        assertEquals(listOf("ok"), proxy.inherited("ok"))
        assertEquals(String::class, handler.function.parameters.single().type.classifier)
        assertEquals("property", proxy.inheritedProperty)
        assertEquals(TypeService::class, handler.property.owner.classifier)
        assertEquals(handler.property, handler.property.normalizeNullability())
        proxy.inheritedProperty = "changed"
        assertEquals("changed", handler.result)
        assertTrue(handler.property.isMutable)
        handler.result = "ok"
        proxy.overloaded(1)
        assertEquals(Int::class, handler.function.parameters.single().type.classifier)
        proxy.overloaded("a")
        assertEquals(String::class, handler.function.parameters.single().type.classifier)
        handler.result = Unit
        proxy.varargs("a", "b")
        assertTrue(handler.function.parameters.single().isVararg)
        assertEquals(listOf("a", "b"), (handler.arguments.single() as Array<*>).toList())
    }

    @Test fun declarationVarianceAndShadowing() {
        handler.result = "ok"
        TypeTestProxy.create<CovariantService<String>>(handler).value()
        assertEquals(KVariance.OUT, handler.function.owner.typeParameters.single().variance)
        val generic = TypeTestProxy.create<GenericService<String>>(handler)
        generic.shadow("ok")
        assertNotEquals(handler.function.owner.typeParameters.single().id, handler.function.typeParameters.single().id)
        handler.result = Unit
        TypeTestProxy.create<ContravariantService<String>>(handler).consume("a")
        assertEquals(KVariance.IN, handler.function.owner.typeParameters.single().variance)
    }

    @Test fun complexAnnotationsValueClassesAndExtensionReceiver() {
        proxy.complex(MetadataId("a"), intArrayOf(3))
        assertEquals(MetadataId::class, handler.function.parameters[0].type.classifier)
        assertEquals(IntArray::class, handler.function.parameters[1].type.classifier)
        val annotation = handler.function.annotations.filterIsInstance<ComplexMetadata>().single()
        assertEquals("nested", annotation.nested.value)
        assertContentEquals(intArrayOf(1, 2), annotation.numbers)
        assertEquals(MetadataMode.FIRST, annotation.mode)
        assertEquals(String::class, annotation.target)
        handler.result = "ok"
        with(proxy) { "receiver".extension(3) }
        assertEquals(String::class, handler.function.receiverType!!.classifier)
        assertEquals(listOf("receiver", 3), handler.arguments)
    }

    @Test fun descriptorInvariants() {
        assertEquals(TypeKind.CLASSIFIER, TypeDescriptor(String::class).kind)
        assertEquals(TypeKind.CLASSIFIER, TypeDescriptor(List::class).kind)
        assertEquals(TypeNullability.NOT_NULL, TypeDescriptor(String::class).nullability)
        val nullable = TypeDescriptor(String::class, nullability = TypeNullability.NULLABLE)
        assertEquals(TypeNullability.NOT_NULL, nullable.copy(nullability = TypeNullability.NOT_NULL).nullability)
        assertFailsWith<IllegalArgumentException> { TypeDescriptor() }
        val id = TypeParameterId("owner", 0)
        assertEquals(TypeKind.PARAMETER, TypeDescriptor(typeParameter = id).kind)
        assertEquals(TypeNullability.UNMARKED, TypeDescriptor(typeParameter = id).nullability)
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(String::class, typeParameter = id) }
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(String::class, nullability = TypeNullability.UNMARKED) }
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(typeParameter = id, nullability = null) }
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(typeParameter = id, typeArguments = listOf(TypeProjection.Star)) }
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(kind = TypeKind.PARAMETER) }
        val dynamic = TypeDescriptor(kind = TypeKind.DYNAMIC, annotations = listOf(MetadataTag("dynamic")))
        assertNull(dynamic.nullability)
        assertNull(dynamic.classifier)
        assertEquals("dynamic", dynamic.annotations.filterIsInstance<MetadataTag>().single().value)
        assertFailsWith<IllegalArgumentException> { dynamic.copy(classifier = String::class) }
        assertFailsWith<IllegalArgumentException> { dynamic.copy(typeParameter = id) }
        assertFailsWith<IllegalArgumentException> { dynamic.copy(nullability = TypeNullability.PLATFORM) }
        assertFailsWith<IllegalArgumentException> { dynamic.copy(typeArguments = listOf(TypeProjection.Star)) }
        assertFailsWith<IllegalArgumentException> { dynamic.copy(functionFlags = FunctionTypeFlags()) }
        assertNotEquals<TypeDescriptor>(dynamic, TypeDescriptor(Any::class, nullability = TypeNullability.NULLABLE))
        assertNotEquals<TypeProjection>(TypeProjection.Star, TypeProjection.Typed(TypeDescriptor(Any::class, nullability = TypeNullability.NULLABLE)))
    }

    @Test fun genericInterfaceAndSuspendDispatch() = runTest {
        handler.result = "ok"
        assertEquals("ok", TypeTestProxy.create<GenericService<String>>(handler).echo("ok"))
        val typeId = assertNotNull(handler.function.returnType.typeParameter)
        handler.result = listOf("ok")
        TypeTestProxy.create<GenericService<String>>(handler).inherited("ok")
        assertEquals(typeId, handler.function.parameters.single().type.typeParameter)
        handler.result = "ok"
        assertEquals(2, handler.function.owner.typeParameters.single().upperBounds.size)
        assertEquals("ok", proxy.suspended())
        assertTrue(handler.function.isSuspend)
    }
}
