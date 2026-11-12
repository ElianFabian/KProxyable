package com.elianfabian.kproxyable.sample

import com.elianfabian.kproxyable.*
import kotlin.reflect.KVariance
import kotlin.test.*

class FunctionDescriptorFactoryTest {
    private val input = TypeProjection.Typed(TypeDescriptor(Int::class))
    private val result = TypeProjection.Typed(TypeDescriptor(String::class))

    @Test fun strictConstructorAndCopyRejectInconsistentFunctionStates() {
        val flags = FunctionTypeFlags()
        val valid = TypeDescriptor.function(listOf(input), result)
        assertFailsWith<IllegalArgumentException> { valid.copy(classifier = Function1::class) }
        assertFailsWith<IllegalArgumentException> { valid.copy(classifier = String::class) }
        assertFailsWith<IllegalArgumentException> { valid.copy(typeArguments = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(typeArguments = listOf(result), functionFlags = FunctionTypeFlags(hasReceiver = true))
        }
        for (variance in listOf(KVariance.IN, KVariance.OUT)) {
            assertFailsWith<IllegalArgumentException> {
                valid.copy(typeArguments = listOf(input.copy(variance = variance), result))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            TypeDescriptor(typeParameter = TypeParameterId("fixture", 0), functionFlags = flags)
        }
        assertFailsWith<IllegalArgumentException> { TypeDescriptor(kind = TypeKind.DYNAMIC, functionFlags = flags) }
        val ordinary = TypeDescriptor(Function::class, typeArguments = listOf(result))
        assertNull(ordinary.functionFlags)
        assertNull(ordinary.functionType)
    }

    @Test fun factoryNormalizesRedundantProjectionsAndRejectsConflictingOnes() {
        val canonical = TypeDescriptor.function(listOf(input), result)
        assertEquals(canonical, TypeDescriptor.function(
            listOf(input.copy(variance = KVariance.IN)), result.copy(variance = KVariance.OUT),
        ))
        assertFailsWith<IllegalArgumentException> {
            TypeDescriptor.function(listOf(input.copy(variance = KVariance.OUT)), result)
        }
        assertFailsWith<IllegalArgumentException> {
            TypeDescriptor.function(listOf(input), result.copy(variance = KVariance.IN))
        }
        assertFailsWith<IllegalArgumentException> {
            TypeDescriptor.function(emptyList(), result, receiverType = input.copy(variance = KVariance.OUT))
        }
        assertFailsWith<IllegalArgumentException> {
            TypeDescriptor.function(emptyList(), result, nullability = TypeNullability.UNMARKED)
        }
    }

    @Test fun starsRetainTheirPositionsAndIdentificationWithoutAPartialSignature() {
        val type = TypeDescriptor.function(listOf(input), TypeProjection.Star, receiverType = TypeProjection.Star)
        assertEquals(Function::class, type.classifier)
        assertEquals(FunctionTypeFlags(hasReceiver = true), type.functionFlags)
        assertEquals(listOf(TypeProjection.Star, input, TypeProjection.Star), type.typeArguments)
        assertNull(type.functionType)
        val large = TypeDescriptor.function(List(23) { input }, result, isSuspend = true)
        assertEquals(23, large.functionType!!.parameters.size)
        assertEquals(Function::class, large.classifier)
    }

    @Test fun factoryRemovesCompilerAnnotationsAtEveryFunctionNodeOnly() {
        val userAnnotation = MetadataTag("user")
        val functionAnnotation = MetadataTag("function")
        val named = input.copy(type = input.type.copy(annotations = listOf(ParameterName("input"), userAnnotation)))
        val collection = TypeDescriptor(List::class, typeArguments = listOf(named))
        // A raw but structurally valid function can contain annotations awaiting normalization.
        val nested = TypeDescriptor(
            Function::class, typeArguments = listOf(named, result), functionFlags = FunctionTypeFlags(),
        )
        val type = TypeDescriptor.function(
            listOf(TypeProjection.Typed(collection)), TypeProjection.Typed(nested),
            receiverType = named, annotations = listOf(ExtensionFunctionType(), functionAnnotation),
        )
        val signature = assertNotNull(type.functionType)
        assertSame(userAnnotation, signature.receiverType!!.annotations.single())
        assertSame(functionAnnotation, type.annotations.single())
        val element = (signature.parameters.single().typeArguments.single() as TypeProjection.Typed).type
        assertEquals(named.type.annotations, element.annotations)
        assertSame(userAnnotation, signature.returnType.functionType!!.parameters.single().annotations.single())
    }

    @Test fun rawEqualityIsStructuralAndDoesNotGuessMissingFlags() {
        val canonical = TypeDescriptor.function(listOf(input), result)
        val raw = TypeDescriptor(Function1::class, typeArguments = listOf(input, result))
        assertNull(raw.functionFlags)
        assertNotEquals(raw, canonical)
        assertEquals(canonical, canonical.copy())
        assertEquals(canonical.hashCode(), canonical.copy().hashCode())
    }

    @Test fun copiedFunctionDerivesItsNewSignatureWithoutChangingTheOriginal() {
        val original = TypeDescriptor.function(listOf(input), result)
        val changed = original.copy(
            typeArguments = listOf(result, input),
            functionFlags = FunctionTypeFlags(isSuspend = true, hasReceiver = true),
            nullability = TypeNullability.NULLABLE,
        )
        val signature = assertNotNull(changed.functionType)
        assertTrue(signature.isSuspend)
        assertEquals(result.type, signature.receiverType)
        assertTrue(signature.parameters.isEmpty())
        assertEquals(input.type, signature.returnType)
        assertEquals(TypeNullability.NULLABLE, changed.nullability)
        assertEquals(listOf(input.type), original.functionType!!.parameters)
        assertEquals(result.type, original.functionType!!.returnType)
        assertNull(changed.copy(typeArguments = listOf(TypeProjection.Star, input)).functionType)
    }
}
