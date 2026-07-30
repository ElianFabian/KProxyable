package com.elianfabian.kproxyable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlin.reflect.KClass

public class KProxyableProcessor(
	private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

	private val accumulatedInterfaces = mutableListOf<KSClassDeclaration>()
	private var registryDeclaration: KSClassDeclaration? = null


	override fun process(resolver: Resolver): List<KSAnnotated> {
		val proxyableAnnotationName = KProxyable::class.qualifiedName ?: return emptyList()
		val registryAnnotationName = KProxyRegistry::class.qualifiedName ?: return emptyList()

		// 1. Process @KProxyRegistry annotation
		val registrySymbols = resolver.getSymbolsWithAnnotation(registryAnnotationName)
			.filterIsInstance<KSClassDeclaration>()
			.toList()

		if (registrySymbols.size > 1) {
			environment.logger.error(
				"Multiple @${KProxyRegistry::class.simpleName} declarations found. Only one registry object per module is allowed.",
				registrySymbols.first(),
			)
		}
		else if (registrySymbols.size == 1) {
			val candidate = registrySymbols.first()
			if (validateRegistryDeclaration(candidate)) {
				registryDeclaration = candidate
			}
		}

		// 2. Process @KProxyable interfaces
		val directSymbols = resolver.getSymbolsWithAnnotation(proxyableAnnotationName)

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

			accumulatedInterfaces.add(classDeclaration)
		}

		return invalid
	}

	private fun validateRegistryDeclaration(declaration: KSClassDeclaration): Boolean {
		var isValid = true

		if (declaration.classKind != ClassKind.OBJECT) {
			environment.logger.error(
				"@${KProxyRegistry::class.simpleName} can only be applied to an 'object' declaration.",
				declaration,
			)
			isValid = false
		}

		if (!declaration.isExpect) {
			environment.logger.error(
				"@${KProxyRegistry::class.simpleName} must be applied to an 'expect' object.",
				declaration,
			)
			isValid = false
		}

		val expectedInterfaceQualifiedName = KProxyFactory::class.qualifiedName
		val implementsInterface = declaration.superTypes.any { superTypeRef ->
			val resolvedType = superTypeRef.resolve()
			resolvedType.declaration.qualifiedName?.asString() == expectedInterfaceQualifiedName
		}

		if (!implementsInterface) {
			environment.logger.error(
				"The expect object annotated with @${KProxyRegistry::class.simpleName} must implement ${KProxyFactory::class.simpleName}.",
				declaration,
			)
			isValid = false
		}

		return isValid
	}

	override fun finish() {
		val registry = registryDeclaration ?: return

		generateActualRegistry(registry, accumulatedInterfaces)
	}

	private fun generateActualRegistry(
		registryDeclaration: KSClassDeclaration,
		interfaces: List<KSClassDeclaration>,
	) {
		val packageName = registryDeclaration.packageName.asString()
		val objectName = registryDeclaration.simpleName.asString()

		val typeVariableT = TypeVariableName("T", ANY)
		val classifierParamType = KClass::class.asClassName().parameterizedBy(typeVariableT)
		val proxyHandlerClassName = ProxyHandler::class.asTypeName()
		val kProxyFactoryClassName = KProxyFactory::class.asTypeName()

		val whenBlock = CodeBlock.builder()
			.beginControlFlow("return when (classifier)")

		interfaces.distinctBy { it.toClassName() }.forEach { interfaceDecl ->
			val interfaceClassName = interfaceDecl.toClassName()
			val interfacePackage = interfaceDecl.packageName.asString()
			val proxyClassName = ClassName(interfacePackage, "_${interfaceDecl.simpleName.asString()}Proxy")

			whenBlock.addStatement("%T::class -> %T(handler) as T", interfaceClassName, proxyClassName)
		}

		whenBlock.addStatement(
			"else -> throw IllegalArgumentException(%S + (classifier as? %T)?.simpleName + %S)",
			"Interface ",
			KClass::class.asClassName().parameterizedBy(STAR),
			" must be annotated with @KProxyable annotation."
		)

		whenBlock.endControlFlow()

		val createFunSpec = FunSpec.builder("createProxy")
			.addModifiers(KModifier.OVERRIDE)
			.addTypeVariable(typeVariableT)
			.addParameter("handler", proxyHandlerClassName)
			.addParameter("classifier", classifierParamType)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.build()
			)
			.returns(typeVariableT)
			.addCode(whenBlock.build())
			.build()

		val actualObjectSpec = TypeSpec.objectBuilder(objectName)
			.addModifiers(KModifier.ACTUAL)
			.addAnnotation(KProxyRegistry::class)
			.addSuperinterface(kProxyFactoryClassName)
			.addFunction(createFunSpec)
			.build()

		val fileSpec = FileSpec.builder(packageName, "${objectName}Actual")
			.addType(actualObjectSpec)
			.build()

		val originatingFiles = (interfaces.mapNotNull { it.containingFile } + listOfNotNull(registryDeclaration.containingFile)).distinct()

		fileSpec.writeTo(
			codeGenerator = environment.codeGenerator,
			aggregating = true,
			originatingKSFiles = originatingFiles,
		)
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
