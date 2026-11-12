package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.test.*

@KProxyable
interface DynamicService {
    fun echo(@MetadataTag("parameter") value: @MetadataTag("type") dynamic): @MetadataTag("return") dynamic
    val current: dynamic
    fun callback(value: (dynamic) -> dynamic)
    fun elements(value: List<dynamic>)
}

private class DynamicHandler : ProxyHandler {
    lateinit var function: FunctionDescriptor
    lateinit var property: PropertyDescriptor
    var result: Any? = null
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        this.function = function
        return result
    }
    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? = onCall(function, args)
    override fun onGetProperty(property: PropertyDescriptor): Any? {
        this.property = property
        return result
    }
    override fun onSetProperty(property: PropertyDescriptor, value: Any?) = Unit
    override fun onEquals(other: Any?): Boolean = false
    override fun onHashCode(): Int = 0
    override fun onToString(): String = "dynamic"
}

class DynamicMetadataTest {
    @Test fun dynamicArgumentsReturnsAndAnnotations() {
        val handler = DynamicHandler()
        val service = TypeTestProxy.create<DynamicService>(handler)
        val value: dynamic = js("({ id: 7 })")
        handler.result = value
        val result = service.echo(value)
        assertEquals(7, result.id as Int)
        val parameter = handler.function.parameters.single()
        assertEquals(TypeKind.DYNAMIC, parameter.type.kind)
        assertNull(parameter.type.classifier)
        assertNull(parameter.type.nullability)
        assertEquals("parameter", parameter.annotations.filterIsInstance<MetadataTag>().single().value)
        assertEquals("type", parameter.type.annotations.filterIsInstance<MetadataTag>().single().value)
        assertEquals(TypeKind.DYNAMIC, handler.function.returnType.kind)
        assertEquals("return", handler.function.returnType.annotations.filterIsInstance<MetadataTag>().single().value)
        handler.result = null
        assertNull(service.echo(null))
    }

    @Test fun dynamicPropertiesAndNestedTypes() {
        val handler = DynamicHandler()
        val service = TypeTestProxy.create<DynamicService>(handler)
        handler.result = "current"
        assertEquals("current", service.current as String)
        assertEquals(TypeKind.DYNAMIC, handler.property.type.kind)
        handler.result = Unit
        service.callback { it }
        val functionType = assertNotNull(handler.function.parameters.single().type.functionType)
        assertEquals(TypeKind.DYNAMIC, functionType.parameters.single().kind)
        assertEquals(TypeKind.DYNAMIC, functionType.returnType.kind)
        service.elements(emptyList())
        val element = handler.function.parameters.single().type.typeArguments.single() as TypeProjection.Typed
        assertEquals(TypeKind.DYNAMIC, element.type.kind)
    }
}
