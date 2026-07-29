package com.elianfabian.kproxyable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

public class KProxyableProcessor(
	private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

	override fun process(resolver: Resolver): List<KSAnnotated> {
		val annotationName = KProxyable::class.qualifiedName ?: return emptyList()

		val directSymbols = resolver.getSymbolsWithAnnotation(annotationName)

		val metaAnnotations = directSymbols
			.filterIsInstance<KSClassDeclaration>()
			.filter { it.classKind == ClassKind.ANNOTATION_CLASS }

		val metaAnnotatedSymbols = metaAnnotations.flatMap { metaAnno ->
			val qualifiedName = metaAnno.qualifiedName?.asString() ?: return@flatMap emptySequence()
			resolver.getSymbolsWithAnnotation(qualifiedName)
		}

		val allTargetSymbols = (directSymbols + metaAnnotatedSymbols)
			.filterNot { it in metaAnnotations }
			.distinct()

		val (valid, invalid) = allTargetSymbols.partition { it.validate() }

		valid.filterIsInstance<KSClassDeclaration>().forEach { classDeclaration ->
			if (classDeclaration.classKind != ClassKind.INTERFACE) {
				environment.logger.error(
					"@${KProxyable::class.qualifiedName} (or meta-annotation) can only be applied to interfaces",
					classDeclaration,
				)
				return@forEach
			}

			generateProxyClass(
				environment = environment,
				classDeclaration = classDeclaration,
			)
		}

		return invalid
	}

	private fun generateProxyClass(
		environment: SymbolProcessorEnvironment,
		classDeclaration: KSClassDeclaration,
	) {
		val packageName = classDeclaration.packageName.asString()
		val interfaceName = classDeclaration.simpleName.asString()
		val proxyClassName = "_${interfaceName}Proxy"
		val interfaceClassName = classDeclaration.toClassName()

		val proxyHandlerClassName = ProxyHandler::class.asTypeName()

		val primaryConstructor = FunSpec.constructorBuilder()
			.addParameter("handler", proxyHandlerClassName)
			.build()

		val handlerProperty = PropertySpec.builder("handler", proxyHandlerClassName)
			.initializer("handler")
			.addModifiers(KModifier.PRIVATE)
			.build()

		val companionObjectSpec = TypeSpec.companionObjectBuilder()
			.apply {
				classDeclaration.getDeclaredFunctions().forEach { function ->
					addProperty(generateFunctionDescriptorStaticProperty(function))
				}
				classDeclaration.getDeclaredProperties().forEach { property ->
					addProperty(generatePropertyDescriptorStaticProperty(property))
				}
			}
			.build()

		val hiddenDeprecatedAnnotation = AnnotationSpec.builder(Deprecated::class)
			.addMember("message = %S", "Internal proxy implementation. Use factory function instead.")
			.addMember("level = %T.%L", DeprecationLevel::class, DeprecationLevel.HIDDEN.name)
			.build()

		val proxyClassSpec = TypeSpec.classBuilder(proxyClassName)
			.addSuperinterface(interfaceClassName)
			.primaryConstructor(primaryConstructor)
			.addProperty(handlerProperty)
			.addType(companionObjectSpec)
			.addAnnotation(hiddenDeprecatedAnnotation)
			.apply {
				classDeclaration.getDeclaredProperties().forEach { property ->
					val propertyName = property.simpleName.asString()
					val descriptorPropertyName = "${propertyName}Descriptor"
					val propertyType = property.type.toTypeName()

					val getterSpec = FunSpec.getterBuilder()
						.addStatement("return handler.onGetProperty(%N) as %T", descriptorPropertyName, propertyType)
						.build()

					val propBuilder = PropertySpec.builder(propertyName, propertyType)
						.addModifiers(KModifier.OVERRIDE)
						.getter(getterSpec)

					if (property.isMutable) {
						val setterSpec = FunSpec.setterBuilder()
							.addParameter("v", propertyType)
							.addStatement("handler.onSetProperty(%N, v)", descriptorPropertyName)
							.build()

						propBuilder
							.mutable(true)
							.setter(setterSpec)
					}

					addProperty(propBuilder.build())
				}

				classDeclaration.getDeclaredFunctions().forEach { function ->
					val functionName = function.simpleName.asString()
					val descriptorPropertyName = "${functionName}Descriptor"
					val returnType = function.returnType?.toTypeName() ?: UNIT

					val parameters = function.parameters.mapNotNull { param ->
						val paramName = param.name?.asString() ?: return@mapNotNull null
						val paramType = param.type.toTypeName()
						ParameterSpec.builder(paramName, paramType).build()
					}

					val argsCall = function.parameters.joinToString(", ") {
						it.name?.asString().orEmpty()
					}

					val isSuspend = Modifier.SUSPEND in function.modifiers
					val statement = if (isSuspend) {
						"return handler.onSuspendCall(%N, listOf(%L)) as %T"
					}
					else {
						"return handler.onCall(%N, listOf(%L)) as %T"
					}

					val funSpec = FunSpec.builder(functionName)
						.addModifiers(
							if (isSuspend) listOf(KModifier.OVERRIDE, KModifier.SUSPEND) else listOf(KModifier.OVERRIDE)
						)
						.returns(returnType)
						.addParameters(parameters)
						.addStatement(statement, descriptorPropertyName, argsCall, returnType)
						.build()

					addFunction(funSpec)
				}

				val equalsSpec = FunSpec.builder("equals")
					.addModifiers(KModifier.OVERRIDE)
					.addParameter("other", ANY.copy(nullable = true))
					.returns(Boolean::class)
					.addStatement("return handler.onEquals(other)")
					.build()

				val hashCodeSpec = FunSpec.builder("hashCode")
					.addModifiers(KModifier.OVERRIDE)
					.returns(Int::class)
					.addStatement("return handler.onHashCode()")
					.build()

				val toStringSpec = FunSpec.builder("toString")
					.addModifiers(KModifier.OVERRIDE)
					.returns(String::class)
					.addStatement("return handler.onToString()")
					.build()

				addFunction(toStringSpec)
				addFunction(equalsSpec)
				addFunction(hashCodeSpec)
			}
			.build()

		val fileSpec = FileSpec.builder(packageName, proxyClassName)
			.addType(proxyClassSpec)
			.build()

		fileSpec.writeTo(
			codeGenerator = environment.codeGenerator,
			aggregating = false,
			originatingKSFiles = listOfNotNull(classDeclaration.containingFile)
		)
	}

	private fun generateTypeDescriptorCode(ksType: KSType): CodeBlock {
		val declaration = ksType.declaration

		val rawClassName = when (declaration) {
			is KSClassDeclaration -> declaration.toClassName()
			is KSTypeParameter -> declaration.bounds.firstOrNull()?.resolve()?.let {
				(it.declaration as? KSClassDeclaration)?.toClassName()
			} ?: ANY
			else -> ANY
		}

		val typeArgsCodeBlocks = ksType.arguments.mapNotNull { arg ->
			arg.type?.resolve()?.let { generateTypeDescriptorCode(it) }
		}

		return CodeBlock.builder().apply {
			add("%T(\n", TypeDescriptor::class.asTypeName())
			indent()
			add("classifier = %T::class,\n", rawClassName)
			add("isNullable = %L", ksType.isMarkedNullable)
			if (typeArgsCodeBlocks.isNotEmpty()) {
				add(",\ntypeArguments = listOf(\n")
				indent()
				typeArgsCodeBlocks.forEach { argCode ->
					add("%L,\n", argCode)
				}
				unindent()
				add(")\n")
			}
			else {
				add("\n")
			}
			unindent()
			add(")")
		}.build()
	}

	private fun generateParameterDescriptorCode(param: KSValueParameter): CodeBlock {
		val paramName = param.name?.asString().orEmpty()
		val paramType = param.type.resolve()
		val typeDescriptorCode = generateTypeDescriptorCode(paramType)

		return CodeBlock.builder().apply {
			add("%T(\n", ParameterDescriptor::class.asTypeName())
			indent()
			add("name = %S,\n", paramName)
			add("type = %L,\n", typeDescriptorCode)
			add("isVararg = %L,\n", param.isVararg)
			add("hasDefault = %L\n", param.hasDefault)
			unindent()
			add(")")
		}.build()
	}

	private fun generatePropertyDescriptorStaticProperty(
		property: KSPropertyDeclaration,
	): PropertySpec {
		val propertyName = property.simpleName.asString()
		val staticPropertyName = "${propertyName}Descriptor"
		val descriptorClassName = PropertyDescriptor::class.asTypeName()

		val typeKSType = property.type.resolve()
		val typeDescriptorCode = generateTypeDescriptorCode(typeKSType)

		val receiverTypeKSType = property.extensionReceiver?.toTypeName() as? KSType
		val receiverTypeCode = receiverTypeKSType?.let { generateTypeDescriptorCode(it) }

		val initializerCode = CodeBlock.builder().apply {
			add("%T(\n", descriptorClassName)
			indent()
			add("name = %S,\n", propertyName)
			add("type = %L,\n", typeDescriptorCode)
			add("isMutable = %L", property.isMutable)
			if (receiverTypeCode != null) {
				add(",\nreceiverType = %L", receiverTypeCode)
			}
			add("\n")
			unindent()
			add(")")
		}.build()

		return PropertySpec.builder(staticPropertyName, descriptorClassName)
			.addModifiers(KModifier.PRIVATE)
			.initializer(initializerCode)
			.build()
	}

	private fun generateFunctionDescriptorStaticProperty(
		function: KSFunctionDeclaration,
	): PropertySpec {
		val functionName = function.simpleName.asString()
		val propertyName = "${functionName}Descriptor"
		val descriptorClassName = FunctionDescriptor::class.asTypeName()

		val returnTypeKSType = function.returnType?.resolve()
		val returnTypeDescriptorCode = if (returnTypeKSType != null) {
			generateTypeDescriptorCode(returnTypeKSType)
		}
		else {
			CodeBlock.of("%T(classifier = %T::class)", TypeDescriptor::class.asTypeName(), UNIT)
		}

		val receiverTypeKSType = function.extensionReceiver?.toTypeName() as? KSType
		val receiverTypeCode = receiverTypeKSType?.let { generateTypeDescriptorCode(it) }

		val paramCodes = function.parameters.map { generateParameterDescriptorCode(it) }

		val initializerCode = CodeBlock.builder().apply {
			add("%T(\n", descriptorClassName)
			indent()
			add("name = %S,\n", functionName)
			add("returnType = %L", returnTypeDescriptorCode)

			if (receiverTypeCode != null) {
				add(",\nreceiverType = %L", receiverTypeCode)
			}

			if (paramCodes.isNotEmpty()) {
				add(",\nparameters = listOf(\n")
				indent()
				paramCodes.forEach { pCode ->
					add("%L,\n", pCode)
				}
				unindent()
				add(")\n")
			}
			else {
				add("\n")
			}

			unindent()
			add(")")
		}.build()

		return PropertySpec.builder(propertyName, descriptorClassName)
			.addModifiers(KModifier.PRIVATE)
			.initializer(initializerCode)
			.build()
	}
}
