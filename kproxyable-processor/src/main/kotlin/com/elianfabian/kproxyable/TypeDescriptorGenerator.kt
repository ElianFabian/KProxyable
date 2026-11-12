package com.elianfabian.kproxyable

import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.reflect.KVariance

/** Generates expressions, not runtime reflection. Parameter references never expand their bounds. */
internal class TypeDescriptorGenerator {
    fun list(items: List<CodeBlock>): CodeBlock = CodeBlock.builder().add("listOf(")
        .apply { items.forEachIndexed { i, item -> if (i > 0) add(", "); add("%L", item) } }.add(")").build()

    fun annotations(node: KSAnnotated): CodeBlock = annotations(node.annotations)

    fun annotations(values: Sequence<KSAnnotation>): CodeBlock = list(values.map {
        val spec = it.toAnnotationSpec()
        val expression = spec.toString().removePrefix("@").trim()
        CodeBlock.of("%L", expression + if (spec.members.isEmpty()) "()" else "")
    }.toList())

    fun parameterId(parameter: KSTypeParameter): CodeBlock {
        val owner = parameter.parentDeclaration ?: error("Type parameter without owner: $parameter")
        val parameters = when (owner) {
            is KSClassDeclaration -> owner.typeParameters
            is KSFunctionDeclaration -> owner.typeParameters
            else -> error("Unsupported type parameter owner: $owner")
        }
        val signature = if (owner is KSFunctionDeclaration) owner.parameters.joinToString(",") { it.type.toString() } else ""
        val id = (owner.qualifiedName?.asString() ?: owner.simpleName.asString()) + "(" + signature + ")"
        return CodeBlock.of("%T(%S, %L)", TypeParameterId::class, id, parameters.indexOf(parameter))
    }

    fun parameter(parameter: KSTypeParameter): CodeBlock = CodeBlock.of(
        "%T(id = %L, name = %S, variance = %T.%L, upperBounds = %L)",
        TypeParameterDescriptor::class, parameterId(parameter), parameter.name.asString(),
        KVariance::class, variance(parameter.variance), list(parameter.bounds.map { type(it.resolve()) }.toList()),
    )

    private fun variance(variance: Variance): String = when (variance) {
        Variance.COVARIANT -> "OUT"
        Variance.CONTRAVARIANT -> "IN"
        else -> "INVARIANT"
    }

    fun reference(value: KSTypeReference, resolved: KSType = value.resolve()): CodeBlock = when (value.element) {
        is KSDynamicReference -> CodeBlock.of(
            "%T(kind = %T.DYNAMIC, annotations = %L)", TypeDescriptor::class, TypeKind::class, annotations(value),
        )
        is KSDefNonNullReference -> CodeBlock.of(
            "%L.copy(nullability = %T.NOT_NULL)", type(resolved), TypeNullability::class,
        )
        else -> type(resolved, emptyMap(), value)
    }

    fun type(value: KSType): CodeBlock = type(value, emptyMap())

    private fun type(value: KSType, substitutions: Map<KSTypeParameter, KSTypeArgument>, source: KSTypeReference? = null): CodeBlock {
        if (source?.element is KSDynamicReference) return reference(source)
        val sourceFunction = source?.element as? KSCallableReference
        val sourceArguments = if (sourceFunction != null)
            listOfNotNull(sourceFunction.receiverType) + sourceFunction.functionParameters.map { it.type } + sourceFunction.returnType
            else source?.element?.typeArguments?.map { it.type }.orEmpty()
        check(!value.isError) { "Unresolved type: $value" }
        val declaration = value.declaration
        if (declaration is KSTypeAlias) {
            val expanded = declaration.type.resolve().let { if (value.isMarkedNullable) it.makeNullable() else it }
            return type(expanded, substitutions + declaration.typeParameters.zip(value.arguments))
        }
        if (declaration is KSTypeParameter && declaration in substitutions) {
            val replacement = requireNotNull(substitutions.getValue(declaration).type) { "Cannot expand star as standalone type: $value" }.resolve()
            return type(if (value.isMarkedNullable) replacement.makeNullable() else replacement, substitutions - declaration)
        }
        // Unknown classifiers are errors, never silently widened to Any.
        check(declaration is KSClassDeclaration || declaration is KSTypeParameter) {
            "Unsupported type classifier: $value (${declaration::class.simpleName})"
        }
        val projections = value.arguments.mapIndexed { index, original ->
            val argument = (original.type?.resolve()?.declaration as? KSTypeParameter)?.let { substitutions[it] } ?: original
            if (argument.variance == Variance.STAR || argument.type == null) CodeBlock.of("%T.Star", TypeProjection::class)
            else {
                val resolved = argument.type!!.resolve().let { if (original.type?.resolve()?.isMarkedNullable == true) it.makeNullable() else it }
                val projectionVariance = if (original.variance == Variance.INVARIANT) argument.variance else original.variance
                CodeBlock.of("%T.Typed(%L, %T.%L)", TypeProjection::class, type(resolved, substitutions, sourceArguments.getOrNull(index)), KVariance::class, variance(projectionVariance))
            }
        }
        val nullability = when {
            value.nullability == Nullability.PLATFORM -> "PLATFORM"
            value.isMarkedNullable -> "NULLABLE"
            declaration is KSTypeParameter -> "UNMARKED"
            else -> "NOT_NULL"
        }
        val classifier = if (declaration is KSClassDeclaration) CodeBlock.of("classifier = %T::class", declaration.toClassName())
            else CodeBlock.of("typeParameter = %L", parameterId(declaration as KSTypeParameter))
        val family = kotlinFunctionFamily(declaration.qualifiedName?.asString())
        if (family != null) {
            val extension = value.annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.ExtensionFunctionType" }
            check(projections.size >= 1 + if (extension) 1 else 0) { "Incomplete function type: $value" }
            return CodeBlock.of(
                "%T.function(parameters = %L, returnType = %L, receiverType = %L, isSuspend = %L, nullability = %T.%L, annotations = %L)",
                TypeDescriptor::class, list(projections.dropLast(1).drop(if (extension) 1 else 0)), projections.last(),
                if (extension) projections.first() else CodeBlock.of("null"), family == KotlinFunctionFamily.SUSPEND,
                TypeNullability::class, nullability, annotations(value.annotations),
            )
        }
        return CodeBlock.of("%T(%L, typeArguments = %L, annotations = %L, nullability = %T.%L)",
            TypeDescriptor::class, classifier, list(projections), annotations(value.annotations), TypeNullability::class, nullability)
    }
}
