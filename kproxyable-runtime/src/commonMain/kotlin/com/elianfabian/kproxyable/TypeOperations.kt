package com.elianfabian.kproxyable

/** Static guarantees after consulting bounds; UNKNOWN does not assert that null is allowed. */
public enum class EffectiveNullability {
    /** Null is excluded, for example String or T with an Any bound. */
    NOT_NULL,

    /** An explicit nullable usage, such as String? or T?. */
    NULLABLE,

    /** An unresolved parameter, platform type, dynamic, or defensive bound cycle. */
    UNKNOWN,
}

/** Declarations indexed by identity, allowing interface and method parameters named T to coexist. */
public typealias TypeParameterScope = Map<TypeParameterId, TypeParameterDescriptor>

/** All parameters available to this method. Bounds already reflect inherited substitutions. */
public fun FunctionDescriptor.typeParameterScope(): TypeParameterScope =
    (owner.typeParameters + typeParameters).associateBy { it.id }

/** Interface parameters available to a property. */
public fun PropertyDescriptor.typeParameterScope(): TypeParameterScope = owner.typeParameters.associateBy { it.id }

/**
 * Simultaneous structural substitution: T -> List<T> is applied once, without revisiting the replacement.
 * Original usage annotations follow replacement annotations. Stars, flags and declaration ids survive.
 * Explicit nullability overrides the replacement; dynamic retains its own typing rules and no mark.
 * For example T? with T -> String becomes String?, and T & Any with T -> String? becomes String.
 */
public fun TypeDescriptor.substitute(substitutions: Map<TypeParameterId, TypeDescriptor>): TypeDescriptor {
    val replacement = typeParameter?.let(substitutions::get)
    if (replacement != null) return replacement.copy(
        nullability = if (replacement.kind == TypeKind.DYNAMIC) null else when (nullability) {
            TypeNullability.UNMARKED -> replacement.nullability
            else -> nullability
        },
        annotations = replacement.annotations + annotations,
    )
    return copy(typeArguments = typeArguments.map { projection ->
        when (projection) {
            TypeProjection.Star -> projection
            is TypeProjection.Typed -> projection.copy(type = projection.type.substitute(substitutions))
        }
    })
}

/** Substitutes constraints without changing this declaration's identity. */
public fun TypeParameterDescriptor.substitute(substitutions: Map<TypeParameterId, TypeDescriptor>): TypeParameterDescriptor =
    copy(upperBounds = upperBounds.map { it.substitute(substitutions) })

/** Substitutes all uses and method bounds; does not recursively substitute introduced replacements. */
public fun FunctionDescriptor.substitute(substitutions: Map<TypeParameterId, TypeDescriptor>): FunctionDescriptor = copy(
    returnType = returnType.substitute(substitutions), receiverType = receiverType?.substitute(substitutions),
    parameters = parameters.map { it.copy(type = it.type.substitute(substitutions)) },
    typeParameters = typeParameters.map { it.substitute(substitutions) },
    owner = owner.copy(typeParameters = owner.typeParameters.map { it.substitute(substitutions) }),
)

/** Substitutes a property and its owner's bounds. */
public fun PropertyDescriptor.substitute(substitutions: Map<TypeParameterId, TypeDescriptor>): PropertyDescriptor = copy(
    type = type.substitute(substitutions),
    owner = owner.copy(typeParameters = owner.typeParameters.map { it.substitute(substitutions) }),
)

/** Resolves guarantees from bounds, not from a runtime value. Empty bounds behave like Any?. */
public fun TypeDescriptor.effectiveNullability(scope: TypeParameterScope = emptyMap()): EffectiveNullability =
    effectiveNullability(scope, emptySet())

private fun TypeDescriptor.effectiveNullability(scope: TypeParameterScope, visited: Set<TypeParameterId>): EffectiveNullability =
    when (nullability) {
        TypeNullability.NOT_NULL -> EffectiveNullability.NOT_NULL
        TypeNullability.NULLABLE -> EffectiveNullability.NULLABLE
        TypeNullability.UNMARKED -> {
            val id = typeParameter
            if (id == null || id in visited) EffectiveNullability.UNKNOWN
            else if (scope[id]?.upperBounds.orEmpty().any {
                it.effectiveNullability(scope, visited + id) == EffectiveNullability.NOT_NULL
            }) EffectiveNullability.NOT_NULL else EffectiveNullability.UNKNOWN
        }
        else -> EffectiveNullability.UNKNOWN
    }

/**
 * Canonicalizes redundant non-null parameter marks according to scope, recursively through arguments.
 * Call after substitution: U & Any becomes U when U : Any. It preserves stars and function flags.
 */
public fun TypeDescriptor.normalizeNullability(scope: TypeParameterScope = emptyMap()): TypeDescriptor = copy(
    nullability = if (kind == TypeKind.PARAMETER && nullability == TypeNullability.NOT_NULL &&
        copy(nullability = TypeNullability.UNMARKED).effectiveNullability(scope) == EffectiveNullability.NOT_NULL
    ) TypeNullability.UNMARKED else nullability,
    typeArguments = typeArguments.map {
        when (it) {
            TypeProjection.Star -> it
            is TypeProjection.Typed -> it.copy(type = it.type.normalizeNullability(scope))
        }
    },
)

/** Normalizes uses and bounds together against a single effective scope. */
public fun FunctionDescriptor.normalizeNullability(): FunctionDescriptor {
    val scope = typeParameterScope()
    fun TypeParameterDescriptor.normalized(): TypeParameterDescriptor = copy(upperBounds = upperBounds.map { it.normalizeNullability(scope) })
    return copy(returnType = returnType.normalizeNullability(scope), receiverType = receiverType?.normalizeNullability(scope),
        parameters = parameters.map { it.copy(type = it.type.normalizeNullability(scope)) },
        typeParameters = typeParameters.map { it.normalized() },
        owner = owner.copy(typeParameters = owner.typeParameters.map { it.normalized() }))
}

/** Normalizes property metadata against its owner's declarations. */
public fun PropertyDescriptor.normalizeNullability(): PropertyDescriptor {
    val scope = typeParameterScope()
    return copy(type = type.normalizeNullability(scope), owner = owner.copy(typeParameters = owner.typeParameters.map { parameter ->
        parameter.copy(upperBounds = parameter.upperBounds.map { it.normalizeNullability(scope) })
    }))
}
