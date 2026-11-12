package com.elianfabian.kproxyable

import kotlin.reflect.KClass
import kotlin.reflect.KVariance

/**
 * Describes an interface method routed to [ProxyHandler.onCall] or [ProxyHandler.onSuspendCall].
 * Unlike [FunctionTypeDescriptor], this describes a callable member, not the type of a callback.
 *
 * For example, `suspend fun find(id: Int): String?` has one parameter of type `Int`, a nullable
 * `String` return type, and [isSuspend] set to `true`.
 *
 * ```kotlin
 * interface Names { suspend fun find(id: Int): String? }
 *
 * val descriptor = FunctionDescriptor(
 *     name = "find",
 *     returnType = TypeDescriptor(String::class, nullability = TypeNullability.NULLABLE),
 *     parameters = listOf(ParameterDescriptor("id", TypeDescriptor(Int::class))),
 *     isSuspend = true,
 *     owner = InterfaceDescriptor(Names::class),
 * )
 * ```
 *
 * @property name Method name. Overloads can share it; it is not a unique member identifier.
 * @property returnType Declared return type after any inherited type substitutions the generator resolves.
 * @property receiverType Extension receiver, as in `fun String.render(): Int`, or `null` for an ordinary
 * method. The proxy instance is not an extension receiver. When present, the generated proxy passes
 * this receiver as the first handler argument, before the ordinary [parameters].
 * @property parameters Ordinary value parameters in declaration order, excluding an extension receiver
 * and any platform-level continuation used to implement a suspend method.
 * @property annotations Annotations on the method itself, not those on its parameter or return types.
 * @property isSuspend Whether the method is declared `suspend`. A normal method accepting a suspend
 * callback still has this set to `false`.
 * @property typeParameters Parameters declared by this method, such as `T` in `fun <T> echo(value: T): T`.
 * Interface parameters are available through [owner] instead.
 * @property owner Interface being proxied. For an inherited method, this is the proxied child interface,
 * not necessarily the interface that originally declared the method. It is always supplied.
 */
public data class FunctionDescriptor(
	val name: String,
	val returnType: TypeDescriptor,
	val receiverType: TypeDescriptor? = null,
	val parameters: List<ParameterDescriptor> = emptyList(),
	val annotations: List<Annotation> = emptyList(),
	val isSuspend: Boolean = false,
	val typeParameters: List<TypeParameterDescriptor> = emptyList(),
	val owner: InterfaceDescriptor,
)

/**
 * Describes one ordinary value parameter of a [FunctionDescriptor]. It stores metadata, not the value
 * passed by the caller; values arrive separately in the handler's argument list.
 *
 * ```kotlin
 * // The parameter in: fun send(message: String = "hello")
 * val parameter = ParameterDescriptor(
 *     name = "message",
 *     type = TypeDescriptor(String::class),
 *     hasDefault = true,
 * )
 * ```
 *
 * @property name Parameter name from the declaration.
 * @property type Declared parameter type. For `vararg names: String`, the generator describes the
 * element type `String`, while the handler receives a single array containing the supplied elements.
 * @property isVararg Whether the parameter is declared `vararg`; this does not flatten handler arguments.
 * @property hasDefault Whether the declaration supplies a default expression. The expression and its
 * value are not stored here, and this descriptor cannot evaluate or invoke that default.
 * @property annotations Annotations on the parameter declaration, separate from annotations on [type].
 */
public data class ParameterDescriptor(
	val name: String,
	val type: TypeDescriptor,
	val isVararg: Boolean = false,
	val hasDefault: Boolean = false,
	val annotations: List<Annotation> = emptyList(),
)

/**
 * Describes a property passed to [ProxyHandler.onGetProperty] or [ProxyHandler.onSetProperty].
 *
 * ```kotlin
 * // Describes: var title: String?
 * val property = PropertyDescriptor(
 *     name = "title",
 *     type = TypeDescriptor(String::class, nullability = TypeNullability.NULLABLE),
 *     isMutable = true,
 *     owner = InterfaceDescriptor(Labels::class), // The interface declaring the property.
 * )
 * ```
 *
 * @property name Property name, shared by its getter and setter.
 * @property type Property value type, including projections and nullability.
 * @property isMutable `true` for `var`, `false` for `val`. This does not describe mutability of the
 * object stored in the property: a `val` may hold a mutable collection.
 * @property annotations Annotations on the property declaration. Getter, setter, and field annotations
 * are not represented as separate collections by this model.
 * @property owner Interface being proxied, including for inherited properties. Always required.
 */
public data class PropertyDescriptor(
	val name: String,
	val type: TypeDescriptor,
	val isMutable: Boolean = false,
	val annotations: List<Annotation> = emptyList(),
	val owner: InterfaceDescriptor,
)

/**
 * Describes the interface for which a proxy is generated and exposes its declaration-level metadata.
 * It does not enumerate members or hold a proxy instance.
 *
 * ```kotlin
 * interface Labels { fun label(): String }
 * val owner = InterfaceDescriptor(classifier = Labels::class)
 * ```
 *
 * For a generic interface such as `Source<out T>`, [typeParameters] describes the declaration of `T`.
 * Creating `Source<String>` through a `KClass` registry does not replace that parameter with `String`.
 *
 * @property classifier Class token identifying the interface, without concrete generic arguments.
 * @property typeParameters Parameters declared by the interface, with their variance and upper bounds.
 * @property annotations Annotations on the interface declaration, available to a handler through its
 * member descriptors' owner. This is not an index of metaannotations or inherited annotations.
 */
public data class InterfaceDescriptor(
	val classifier: KClass<*>,
	val typeParameters: List<TypeParameterDescriptor> = emptyList(),
	val annotations: List<Annotation> = emptyList(),
)

/**
 * Describes a type usage, such as `String?`, `List<out Number>`, or a reference to a generic parameter.
 * [kind] selects a classifier, a parameter reference, or Kotlin/JS `dynamic`. Construction validates
 * the fields for that kind. This keeps dynamic separate from platform nullability.
 *
 * ```kotlin
 * // List<String?>
 * val strings = TypeDescriptor(
 *     classifier = List::class,
 *     typeArguments = listOf(
 *         TypeProjection.Typed(
 *             TypeDescriptor(String::class, nullability = TypeNullability.NULLABLE),
 *         ),
 *     ),
 * )
 *
 * // Unmodified usage of T, whose declaration is described elsewhere.
 * val parameterType = TypeDescriptor(typeParameter = TypeParameterId("example.Source()", 0))
 * ```
 *
 * @property classifier Class token for a class-based type; `null` for a type parameter reference or dynamic.
 * Type aliases are expanded by the generator, whereas value classes keep their own class identity.
 * With [functionFlags], this is always the logical marker `Function::class`, not the runtime class
 * of a callback. Without flags, that same token can describe the ordinary supertype `Function<R>`.
 * @property typeArguments Ordered generic arguments, including stars. For example, `Map<*, String>`
 * has two entries, the first being [TypeProjection.Star]. Only [TypeKind.CLASSIFIER] accepts arguments.
 * These are usages, not parameter declarations. For a function, the order is receiver (if present),
 * ordinary parameters, then result. There is never an implicit `Continuation` entry.
 * @property typeParameter Reference to a declared type parameter; `null` for a classifier or dynamic type.
 * Resolve its declaration through the owning method or interface's type parameters.
 * @property annotations Annotations on this type usage. In `List<@Sensitive String>`, the annotation
 * belongs to the nested `String` descriptor, not the outer `List` descriptor.
 * @property kind Structural kind of the type. Classes and interfaces both use [TypeKind.CLASSIFIER].
 * Defaults to [TypeKind.PARAMETER] if [typeParameter] is supplied, otherwise [TypeKind.CLASSIFIER].
 * Specify [TypeKind.DYNAMIC] explicitly for a dynamic type, with neither classifier nor parameter.
 * @property nullability Nullability of this usage. Defaults to [TypeNullability.UNMARKED] for a
 * parameter and [TypeNullability.NOT_NULL] for a classifier. It is `null` only for [TypeKind.DYNAMIC],
 * whose typing rules are not a nullable/non-null distinction. It does not infer nullability from bounds.
 * @property functionFlags Present for every ordinary or suspend Kotlin function type, even when both
 * flags are false. Absent for reflection types, SAM interfaces, and the ordinary `Function<R>` supertype.
 * Use [function] to construct canonical function metadata manually. Raw constructor equality is structural;
 * omitted flags cannot be inferred reliably from arbitrary runtime class tokens.
 */
public data class TypeDescriptor(
	val classifier: KClass<*>? = null,
	val typeArguments: List<TypeProjection> = emptyList(),
	val typeParameter: TypeParameterId? = null,
	val annotations: List<Annotation> = emptyList(),
	val kind: TypeKind = if (typeParameter != null) TypeKind.PARAMETER else TypeKind.CLASSIFIER,
	val nullability: TypeNullability? = when (kind) {
		TypeKind.PARAMETER -> TypeNullability.UNMARKED
		TypeKind.CLASSIFIER -> TypeNullability.NOT_NULL
		TypeKind.DYNAMIC -> null
	},
	val functionFlags: FunctionTypeFlags? = null,
) {
	init {
		when (kind) {
			TypeKind.CLASSIFIER -> {
				require(classifier != null && typeParameter == null) { "CLASSIFIER requires only a classifier" }
				require(nullability != null && nullability != TypeNullability.UNMARKED) {
					"CLASSIFIER requires explicit nullable, non-null or platform nullability"
				}
			}
			TypeKind.PARAMETER -> {
				require(typeParameter != null && classifier == null) { "PARAMETER requires only a parameter reference" }
				require(nullability != null) { "PARAMETER requires nullability" }
				require(typeArguments.isEmpty() && functionFlags == null) { "PARAMETER has no arguments or function flags" }
			}
			TypeKind.DYNAMIC -> {
				require(classifier == null && typeParameter == null) { "DYNAMIC has no classifier or parameter reference" }
				require(nullability == null) { "DYNAMIC has no independent nullability" }
				require(typeArguments.isEmpty() && functionFlags == null) { "DYNAMIC has no arguments or function flags" }
			}
		}
		if (functionFlags != null) {
			require(kind == TypeKind.CLASSIFIER && classifier == Function::class) {
				"Function flags require the logical Function::class classifier"
			}
			require(typeArguments.size >= 1 + if (functionFlags.hasReceiver) 1 else 0) {
				"A function requires a result and, when flagged, a receiver position"
			}
			require(typeArguments.all { it is TypeProjection.Star || (it as TypeProjection.Typed).variance == KVariance.INVARIANT }) {
				"Function arguments must be stars or invariant; use TypeDescriptor.function to normalize projections"
			}
		}
	}

	/**
	 * A signature derived solely from [typeArguments] and [functionFlags]. Returns `null` for an ordinary
	 * classifier/parameter/dynamic type or if any function argument is a star. Nullability of the callback
	 * value remains on this descriptor. The handler receives the original object, so invoking a suspend
	 * callback requires a cast to its actual `suspend` type, never to this logical marker's runtime class.
	 */
	public val functionType: FunctionTypeDescriptor?
		get() {
			val flags = functionFlags ?: return null
			if (typeArguments.any { it is TypeProjection.Star }) return null
			val types = typeArguments.map { (it as TypeProjection.Typed).type }
			return FunctionTypeDescriptor(
				parameters = types.dropLast(1).drop(if (flags.hasReceiver) 1 else 0),
				returnType = types.last(),
				receiverType = if (flags.hasReceiver) types.first() else null,
				isSuspend = flags.isSuspend,
			)
		}

	public companion object {
		/**
		 * Builds canonical logical metadata, including projected or unknown argument positions.
		 * Input `in` and result `out` projections are redundant and become invariant; opposite projections
		 * are rejected. Stars remain stars. A non-null [receiverType], including a star, supplies the receiver.
		 * Compiler `ParameterName` annotations are removed from direct inputs at each function node;
		 * annotations nested in ordinary classifiers remain intact. `ExtensionFunctionType` is represented
		 * by [FunctionTypeFlags.hasReceiver], not duplicated in the function's annotations.
		 *
		 * ```kotlin
		 * val callback = TypeDescriptor.function(
		 *     parameters = listOf(TypeProjection.Typed(TypeDescriptor(Int::class))),
		 *     returnType = TypeProjection.Typed(TypeDescriptor(String::class)),
		 * ) // (Int) -> String, including non-null flags with both values false
		 * ```
		 */
		public fun function(
			parameters: List<TypeProjection>,
			returnType: TypeProjection,
			receiverType: TypeProjection? = null,
			isSuspend: Boolean = false,
			nullability: TypeNullability = TypeNullability.NOT_NULL,
			annotations: List<Annotation> = emptyList(),
		): TypeDescriptor {
			val inputs = (listOfNotNull(receiverType) + parameters).map { canonicalFunctionProjection(it, true) }
			return TypeDescriptor(
				classifier = Function::class,
				typeArguments = inputs + canonicalFunctionProjection(returnType, false),
				nullability = nullability,
				annotations = annotations.filterNot { it is ExtensionFunctionType },
				functionFlags = FunctionTypeFlags(isSuspend, receiverType != null),
			)
		}
	}
}

/**
 * The non-type structure of a Kotlin function signature. Presence is significant: `(Int) -> String`
 * uses `FunctionTypeFlags()` even though both booleans are false. Parameter and result types are stored
 * only in [TypeDescriptor.typeArguments], not duplicated here.
 *
 * @property isSuspend Whether the logical type is `suspend`; it does not add a Continuation argument.
 * @property hasReceiver Whether the first argument position is an extension receiver. It is supplied
 * explicitly by the factory or read from ExtensionFunctionType by KSP, never inferred from arity.
 */
public data class FunctionTypeFlags(
	val isSuspend: Boolean = false,
	val hasReceiver: Boolean = false,
)

private fun canonicalFunctionProjection(projection: TypeProjection, input: Boolean): TypeProjection = when (projection) {
	TypeProjection.Star -> projection
	is TypeProjection.Typed -> {
		require(projection.variance == KVariance.INVARIANT || projection.variance == if (input) KVariance.IN else KVariance.OUT) {
			"Conflicting projection in function ${if (input) "input" else "result"}"
		}
		val type = canonicalFunctionNodes(projection.type)
		projection.copy(
			type = if (input) type.copy(annotations = type.annotations.filterNot { it is ParameterName }) else type,
			variance = KVariance.INVARIANT,
		)
	}
}

private fun canonicalFunctionNodes(type: TypeDescriptor): TypeDescriptor {
	val arguments = type.typeArguments.map { projection ->
		when (projection) {
			TypeProjection.Star -> projection
			is TypeProjection.Typed -> projection.copy(type = canonicalFunctionNodes(projection.type))
		}
	}
	return if (type.functionFlags == null) type.copy(typeArguments = arguments) else type.copy(
		typeArguments = arguments.mapIndexed { index, projection ->
			if (index < arguments.lastIndex && projection is TypeProjection.Typed)
				projection.copy(type = projection.type.copy(annotations = projection.type.annotations.filterNot { it is ParameterName }))
			else projection
		},
		annotations = type.annotations.filterNot { it is ExtensionFunctionType },
	)
}

/** Structural kind of a [TypeDescriptor], independent of nullability or compilation target. */
public enum class TypeKind {
	/** A class, interface, object, enum, or other class-based classifier, such as `String` or `List<Int>`. */
	CLASSIFIER,

	/** A reference to a declared type parameter, such as `T`, `T?`, or `T & Any`. */
	PARAMETER,

	/**
	 * Kotlin/JS `dynamic`, which disables static checking of operations and nullability.
	 * It has no class token or generic arguments, but can retain type-use annotations.
	 * This is not Java-style platform nullability and is not supported by Kotlin/Wasm.
	 */
	DYNAMIC,
}

/**
 * Describes one generic argument at its usage site: `String`, `in String`, `out Number`, or `*`.
 * Declaration-site variance belongs to [TypeParameterDescriptor.variance] instead.
 *
 * ```kotlin
 * val unknown = TypeProjection.Star // The argument of List<*>.
 * val numbers = TypeProjection.Typed(TypeDescriptor(Number::class), KVariance.OUT)
 * // numbers represents the argument of Array<out Number>.
 * ```
 */
public sealed interface TypeProjection {
	/**
	 * Star projection, as in `List<*>`. It has no concrete argument type and is not interchangeable
	 * with [Typed] containing nullable `Any`; its meaning depends on the declared parameter bounds.
	 */
	public data object Star : TypeProjection

	/**
	 * A concrete or parameter-based type argument, optionally projected with `in` or `out`.
	 *
	 * @property type Argument type, such as `String?` in `List<String?>` or `T` in `List<T>`.
	 * @property variance Explicit use-site projection: [KVariance.INVARIANT] for `Array<String>`,
	 * [KVariance.IN] for `Array<in String>`, or [KVariance.OUT] for `Array<out String>`.
	 * This does not copy variance from the generic class's parameter declaration.
	 */
	public data class Typed(
		val type: TypeDescriptor,
		val variance: KVariance = KVariance.INVARIANT,
	) : TypeProjection
}

/**
 * Identifies a type parameter by its declaring context and position, rather than just its name.
 * A method's `T` and an interface's `T` can therefore be referenced independently.
 *
 * ```kotlin
 * val id = TypeParameterId(declarationId = "example.Source()", index = 0)
 * val reference = TypeDescriptor(typeParameter = id)
 * ```
 *
 * The example identifier is illustrative. Generated identifiers use KSP declaration names and
 * signatures; they are not a stable serialization format across compiler or processor versions.
 * References to parameters do not recursively embed their bounds, which permits `Comparable<T>`.
 *
 * @property declarationId Identifier of the declaring interface or method, including method parameter
 * signature information supplied by the generator. Consumers should not parse its textual format.
 * @property index Zero-based position in that declaration's type parameter list; for `<K, V>`,
 * `K` has index `0` and `V` has index `1`.
 */
public data class TypeParameterId(val declarationId: String, val index: Int)

/**
 * Describes a generic parameter declaration, including variance and all upper-bound constraints.
 * A usage of this parameter is a [TypeDescriptor] referencing [id], not another copy of this declaration.
 *
 * ```kotlin
 * // The T declaration in: interface Source<out T : CharSequence>
 * val parameter = TypeParameterDescriptor(
 *     id = TypeParameterId("example.Source()", 0),
 *     name = "T",
 *     variance = KVariance.OUT,
 *     upperBounds = listOf(TypeDescriptor(CharSequence::class)),
 * )
 * ```
 *
 * In `where T : CharSequence, T : Comparable<T>`, [upperBounds] contains both types. The nested `T`
 * references [id], preventing infinite expansion of recursive bounds.
 *
 * @property id Declaration-scoped identity used by parameter references.
 * @property name Source name, useful for display but insufficient as a unique identity.
 * @property variance Declaration-site variance: `INVARIANT` for `<T>`, `IN` for `<in T>`, and `OUT`
 * for `<out T>`. This is distinct from the projection on an individual use of the generic type.
 * @property upperBounds Constraints declared inline or in `where`. Every bound must be satisfied.
 * The generator includes the implicit `Any?` bound exposed by KSP for an unconstrained parameter;
 * the empty default here does not itself calculate or insert that bound.
 */
public data class TypeParameterDescriptor(
	val id: TypeParameterId,
	val name: String,
	val variance: KVariance = KVariance.INVARIANT,
	val upperBounds: List<TypeDescriptor> = emptyList(),
)

/**
 * Describes nullability at a type usage, independently of its class or type parameter identity.
 *
 * ```kotlin
 * val nullableString = TypeDescriptor(String::class, nullability = TypeNullability.NULLABLE)
 * val id = TypeParameterId("example.Source()", 0)
 * val originalT = TypeDescriptor(typeParameter = id) // UNMARKED
 * val nonNullT = originalT.copy(nullability = TypeNullability.NOT_NULL) // T & Any when needed
 * ```
 *
 * [UNMARKED] and [PLATFORM] cannot be interpreted as a simple boolean saying whether null is allowed.
 * A [TypeKind.DYNAMIC] descriptor has no value of this enum; its [TypeDescriptor.nullability] is null.
 */
public enum class TypeNullability {
	/**
	 * Excludes null, as in `String` or the non-null part of a parameter, `T & Any`.
	 * For a parameter reference, its declaration bounds determine whether `& Any` is needed
	 * when rendering the type. This does not change other usages of that parameter.
	 */
	NOT_NULL,

	/** Explicitly nullable usage, such as `String?`, `T?`, or the element in `List<String?>`. */
	NULLABLE,

	/**
	 * Unmodified parameter usage, such as `T` in `fun <T> echo(value: T): T`.
	 * Nullability follows the parameter's bounds and eventual type argument. It is not an
	 * assertion that null is allowed: `T : Any` also uses this state but excludes null.
	 */
	UNMARKED,

	/**
	 * Flexible platform nullability reported by KSP, for example Java's `String!` without
	 * nullability annotations. Neither nullable nor non-null is guaranteed by this state.
	 * This flag does not store the lower and upper flexible type bounds, or identify raw Java
	 * generic arguments; it records only nullability flexibility.
	 */
	PLATFORM,
}

/**
 * Describes the signature of a function-valued type, not an intercepted interface method.
 * It is the derived view exposed by [TypeDescriptor.functionType] for callbacks, returns, or properties.
 * Unlike [FunctionDescriptor], it has no member name, interface owner, or executable implementation.
 *
 * ```kotlin
 * // Describes: suspend String.(Int) -> Boolean
 * val callback = FunctionTypeDescriptor(
 *     parameters = listOf(TypeDescriptor(Int::class)),
 *     returnType = TypeDescriptor(Boolean::class),
 *     receiverType = TypeDescriptor(String::class),
 *     isSuspend = true,
 * )
 * ```
 *
 * A method `fun execute(callback: suspend () -> Unit)` has [FunctionDescriptor.isSuspend] set to
 * `false`, while its callback parameter's function type has [isSuspend] set to `true`.
 * This descriptor does not invoke callbacks or make suspend functions callable synchronously.
 *
 * @property parameters Ordinary function argument types in order, excluding [receiverType] and any
 * platform-level continuation. Parameter names and default expressions are not stored.
 * @property returnType Function result type; even a function returning no useful value has `Unit` here.
 * @property receiverType Extension receiver, such as `String` in `String.(Int) -> Boolean`, or `null`
 * for a function type such as `(Int) -> Boolean`.
 * @property isSuspend Whether the function type itself is declared `suspend`.
 * Nullability of the function value belongs to its enclosing [TypeDescriptor], not this signature.
 */
public data class FunctionTypeDescriptor(
	val parameters: List<TypeDescriptor>,
	val returnType: TypeDescriptor,
	val receiverType: TypeDescriptor? = null,
	val isSuspend: Boolean = false,
)
