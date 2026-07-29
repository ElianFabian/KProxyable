package com.elianfabian.kproxyable

import kotlin.reflect.KClass

public data class FunctionDescriptor(
	val name: String,
	val returnType: TypeDescriptor,
	val receiverType: TypeDescriptor? = null,
	val parameters: List<ParameterDescriptor> = emptyList(),
	val annotations: List<Annotation> = emptyList()
)

public data class ParameterDescriptor(
	val name: String,
	val type: TypeDescriptor,
	val isVararg: Boolean = false,
	val hasDefault: Boolean = false,
	val annotations: List<Annotation> = emptyList(),
)

public data class PropertyDescriptor(
	val name: String,
	val type: TypeDescriptor,
	val isMutable: Boolean = false,
	val receiverType: TypeDescriptor? = null,
	val annotations: List<Annotation> = emptyList()
)

public data class TypeDescriptor(
	val classifier: KClass<*>,
	val isNullable: Boolean = false,
	val typeArguments: List<TypeDescriptor> = emptyList(),
)
