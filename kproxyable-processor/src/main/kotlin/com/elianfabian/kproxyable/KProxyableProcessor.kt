package com.elianfabian.kproxyable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
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
import java.io.File
import java.util.zip.ZipFile
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
		val moduleName = environment.options["kproxyable.moduleName"] ?: "unknown"
		val isApp = environment.options["kproxyable.isApp"] == "true"

		// 1. Always generate a unique registry for the current module
		if (accumulatedInterfaces.isNotEmpty()) {
			generateModuleRegistry(moduleName, accumulatedInterfaces)
			// 2. Generate a "breadcrumb" file in META-INF/services to allow other modules to discover this registry
			generateBreadcrumb(moduleName)
		}

		val registry = registryDeclaration
		if (registry != null) {
			// Manual KMP Way: The user provided an 'expect object' annotated with @KProxyRegistry
			generateCompositeActualRegistry(registry, accumulatedInterfaces)
		} else if (isApp) {
			// Automagic Way: This is an application module, generate the platform-specific Master Registry
			val platforms = environment.platforms.map { it.platformName.lowercase() }
			if (platforms.any { it.contains("js") }) {
				generateJsImpl()
			} else {
				generateJvmImpl()
			}
		}
	}

	/**
	 * Generates the master entry point for JS projects.
	 * Includes a root-level discovery function to bypass the lack of reflection.
	 */
	private fun generateJsImpl() {
		val packageName = "com.elianfabian.kproxyable.generated"
		val objectName = "KProxyJsImpl"
		val moduleName = environment.options["kproxyable.moduleName"] ?: "unknown"

		// Discover all module registries
		val discoveredRegistryFqns = discoverRegistries().toMutableSet()

		// Add current module's registry if it contains interfaces
		if (accumulatedInterfaces.isNotEmpty()) {
			discoveredRegistryFqns.add("com.elianfabian.kproxyable.generated.KProxyRegistry_$moduleName")
		}

		val typeVariableT = TypeVariableName("T", ANY)
		val classifierParamType = KClass::class.asClassName().parameterizedBy(typeVariableT)
		val proxyHandlerClassName = ProxyHandler::class.asTypeName()

		val codeBlock = CodeBlock.builder()

		if (discoveredRegistryFqns.isEmpty()) {
			codeBlock.addStatement("return null")
		} else {
			codeBlock.add("return ")
			discoveredRegistryFqns.forEachIndexed { index, fqn ->
				val discoveredRegistry = ClassName.bestGuess(fqn)
				codeBlock.add("%T.findProxy(handler, classifier)", discoveredRegistry)
				if (index < discoveredRegistryFqns.size - 1) {
					codeBlock.add("\n ?: ")
				}
			}
			codeBlock.add("\n")
		}

		val findFunSpec = FunSpec.builder("findProxy")
			.addModifiers(KModifier.OVERRIDE)
			.addTypeVariable(typeVariableT)
			.addParameter("handler", proxyHandlerClassName)
			.addParameter("classifier", classifierParamType)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.build()
			)
			.returns(typeVariableT.copy(nullable = true))
			.addCode(codeBlock.build())
			.build()

		val jsExport = ClassName("kotlin.js", "JsExport")
		val jsName = ClassName("kotlin.js", "JsName")

		val objectSpec = TypeSpec.objectBuilder(objectName)
			.addAnnotation(jsExport)
			.addAnnotation(AnnotationSpec.builder(jsName).addMember("%S", objectName).build())
			.addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(findFunSpec)
			.build()

		val fileSpec = FileSpec.builder(packageName, objectName)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.addMember("%S", "NON_EXPORTABLE_TYPE")
					.addMember("%S", "OPT_IN_USAGE")
					.addMember("%S", "OPT_IN_USAGE_ERROR")
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addAnnotation(
				AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
					.addMember("%T::class", ClassName("kotlin.js", "ExperimentalJsExport"))
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addType(objectSpec)
			.build()

		val discoveryFileSpec = FileSpec.builder("", "${objectName}_Discovery")
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.addMember("%S", "NON_EXPORTABLE_TYPE")
					.addMember("%S", "OPT_IN_USAGE")
					.addMember("%S", "OPT_IN_USAGE_ERROR")
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addAnnotation(
				AnnotationSpec.builder(ClassName("kotlin", "OptIn"))
					.addMember("%T::class", ClassName("kotlin.js", "ExperimentalJsExport"))
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addFunction(
				FunSpec.builder("getKProxyJsImpl")
					.addAnnotation(jsExport)
					.addAnnotation(AnnotationSpec.builder(jsName).addMember("%S", "getKProxyJsImpl").build())
					.returns(KProxyFactory::class.asTypeName())
					.addStatement("return %T", ClassName(packageName, objectName))
					.build()
			)
			.build()

		fileSpec.writeTo(environment.codeGenerator, aggregating = true)
		discoveryFileSpec.writeTo(environment.codeGenerator, aggregating = true)
	}


	private fun generateModuleRegistry(moduleName: String, interfaces: List<KSClassDeclaration>) {
		val packageName = "com.elianfabian.kproxyable.generated"
		val objectName = "KProxyRegistry_$moduleName"

		val findFunSpec = generateFindProxyFunction(interfaces)

		val objectSpec = TypeSpec.objectBuilder(objectName)
			.addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(findFunSpec)
			.build()

		val fileSpec = FileSpec.builder(packageName, objectName)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addType(objectSpec)
			.build()

		fileSpec.writeTo(
			codeGenerator = environment.codeGenerator,
			aggregating = true,
			originatingKSFiles = interfaces.mapNotNull { it.containingFile }.distinct(),
		)
	}

	private fun generateJvmImpl() {
		val packageName = "com.elianfabian.kproxyable.generated"
		val objectName = "KProxyJvmImpl"
		val moduleName = environment.options["kproxyable.moduleName"] ?: "unknown"

		// Discover all module registries
		val discoveredRegistryFqns = discoverRegistries().toMutableSet()

		// Add current module's registry if it contains interfaces
		if (accumulatedInterfaces.isNotEmpty()) {
			discoveredRegistryFqns.add("com.elianfabian.kproxyable.generated.KProxyRegistry_$moduleName")
		}

		val typeVariableT = TypeVariableName("T", ANY)
		val classifierParamType = KClass::class.asClassName().parameterizedBy(typeVariableT)
		val proxyHandlerClassName = ProxyHandler::class.asTypeName()

		val codeBlock = CodeBlock.builder()

		if (discoveredRegistryFqns.isEmpty()) {
			codeBlock.addStatement("return null")
		} else {
			codeBlock.add("return ")
			discoveredRegistryFqns.forEachIndexed { index, fqn ->
				val discoveredRegistry = ClassName.bestGuess(fqn)
				codeBlock.add("%T.findProxy(handler, classifier)", discoveredRegistry)
				if (index < discoveredRegistryFqns.size - 1) {
					codeBlock.add("\n ?: ")
				}
			}
			codeBlock.add("\n")
		}

		val findFunSpec = FunSpec.builder("findProxy")
			.addModifiers(KModifier.OVERRIDE)
			.addTypeVariable(typeVariableT)
			.addParameter("handler", proxyHandlerClassName)
			.addParameter("classifier", classifierParamType)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.build()
			)
			.returns(typeVariableT.copy(nullable = true))
			.addCode(codeBlock.build())
			.build()

		val objectSpec = TypeSpec.objectBuilder(objectName)
			.addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(findFunSpec)
			.build()

		val fileSpec = FileSpec.builder(packageName, objectName)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addType(objectSpec)
			.build()

		fileSpec.writeTo(environment.codeGenerator, aggregating = true)
	}

	private fun generateBreadcrumb(moduleName: String) {
		val fqn = "com.elianfabian.kproxyable.generated.KProxyRegistry_$moduleName"
		environment.codeGenerator.createNewFile(
			dependencies = Dependencies(true, *accumulatedInterfaces.mapNotNull { it.containingFile }.toTypedArray()),
			packageName = "META-INF.services",
			fileName = "com.elianfabian.kproxyable.KProxyFactory",
			extensionName = ""
		).use { output ->
			output.write(fqn.toByteArray())
		}
	}

	/**
	 * Scans the full classpath for "breadcrumb" files generated by other modules.
	 * 
	 * CRITICAL: This method searches both the root (for JARs) and 'default/resources/' 
	 * (for JS/KMP Klibs) to find the ServiceLoader-style metadata.
	 */
	private fun discoverRegistries(): Set<String> {
		val discoveredRegistryFqns = mutableSetOf<String>()
		val path = "META-INF/services/com.elianfabian.kproxyable.KProxyFactory"
		val klibPath = "default/resources/$path"

		// 1. Try standard ClassLoader discovery
		try {
			val resources = this::class.java.classLoader.getResources(path)
			while (resources.hasMoreElements()) {
				val url = resources.nextElement()
				environment.logger.warn("Discovered breadcrumb via ClassLoader: $url")
				url.openStream().bufferedReader().useLines { lines ->
					discoveredRegistryFqns.addAll(lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") })
				}
			}
		} catch (e: Exception) { }

		// 2. Try explicit classpath argument from Gradle plugin
		val explicitClasspath = environment.options["kproxyable.classpath"]
		if (explicitClasspath != null) {
			explicitClasspath.split(File.pathSeparator).forEach { item ->
				if (item.isBlank()) return@forEach
				val file = File(item)
				if (file.exists()) {
					if (file.isDirectory) {
						val breadcrumb = File(file, path)
						if (breadcrumb.exists()) {
							breadcrumb.useLines { lines ->
								discoveredRegistryFqns.addAll(lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") })
							}
						}
					} else if (file.extension == "jar" || file.extension == "klib") {
						try {
							ZipFile(file).use { zip ->
								val entry = zip.getEntry(path) ?: zip.getEntry(klibPath)
								if (entry != null) {
									zip.getInputStream(entry).bufferedReader().useLines { lines ->
										discoveredRegistryFqns.addAll(lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") })
									}
								}
							}
						} catch (e: Exception) { }
					}
				}
			}
		}
		return discoveredRegistryFqns
	}

	private fun generateCompositeActualRegistry(
		registryDeclaration: KSClassDeclaration,
		localInterfaces: List<KSClassDeclaration>,
	) {
		val packageName = registryDeclaration.packageName.asString()
		val objectName = registryDeclaration.simpleName.asString()
		val moduleName = environment.options["kproxyable.moduleName"] ?: "unknown"

		// Discover other registries from dependencies
		val discoveredRegistryFqns = discoverRegistries().toMutableSet()
		
		// Add the local module registry if it contains interfaces
		if (localInterfaces.isNotEmpty()) {
			discoveredRegistryFqns.add("com.elianfabian.kproxyable.generated.KProxyRegistry_$moduleName")
		}

		val typeVariableT = TypeVariableName("T", ANY)
		val classifierParamType = KClass::class.asClassName().parameterizedBy(typeVariableT)
		val proxyHandlerClassName = ProxyHandler::class.asTypeName()

		val codeBlock = CodeBlock.builder()

		if (discoveredRegistryFqns.isEmpty()) {
			codeBlock.addStatement("return null")
		} else {
			codeBlock.add("return ")
			discoveredRegistryFqns.toList().forEachIndexed { index, fqn ->
				val discoveredRegistry = ClassName.bestGuess(fqn)
				codeBlock.add("%T.findProxy(handler, classifier)", discoveredRegistry)
				if (index < discoveredRegistryFqns.size - 1) {
					codeBlock.add("\n ?: ")
				}
			}
			codeBlock.add("\n")
		}

		val findFunSpec = FunSpec.builder("findProxy")
			.addModifiers(KModifier.OVERRIDE)
			.addTypeVariable(typeVariableT)
			.addParameter("handler", proxyHandlerClassName)
			.addParameter("classifier", classifierParamType)
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.build()
			)
			.returns(typeVariableT.copy(nullable = true))
			.addCode(codeBlock.build())
			.build()

		val actualObjectSpec = TypeSpec.objectBuilder(objectName)
			.addModifiers(KModifier.ACTUAL)
			.addAnnotation(KProxyRegistry::class)
			.addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(findFunSpec)
			.build()

		val fileSpec = FileSpec.builder(packageName, "${objectName}Actual")
			.addAnnotation(
				AnnotationSpec.builder(Suppress::class)
					.addMember("%S", "DEPRECATION")
					.addMember("%S", "DEPRECATION_ERROR")
					.addMember("%S", "UNCHECKED_CAST")
					.useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
					.build()
			)
			.addType(actualObjectSpec)
			.build()

		val originatingFiles = (localInterfaces.mapNotNull { it.containingFile } + listOfNotNull(registryDeclaration.containingFile)).distinct()

		fileSpec.writeTo(
			codeGenerator = environment.codeGenerator,
			aggregating = true,
			originatingKSFiles = originatingFiles,
		)
	}

	private fun generateFindProxyFunction(
		interfaces: List<KSClassDeclaration>,
	): FunSpec {
		val typeVariableT = TypeVariableName("T", ANY)
		val classifierParamType = KClass::class.asClassName().parameterizedBy(typeVariableT)
		val proxyHandlerClassName = ProxyHandler::class.asTypeName()

		val whenBlock = CodeBlock.builder()
			.beginControlFlow("return when (classifier)")

		interfaces.distinctBy { it.toClassName() }.forEach { interfaceDecl ->
			val interfaceClassName = interfaceDecl.toClassName()
			val interfacePackage = interfaceDecl.packageName.asString()
			val proxyClassName = ClassName(interfacePackage, "_${interfaceDecl.simpleName.asString()}Proxy")

			whenBlock.addStatement("%T::class -> %T(handler) as T", interfaceClassName, proxyClassName)
		}

		whenBlock.addStatement("else -> null")
		whenBlock.endControlFlow()

		return FunSpec.builder("findProxy")
			.addModifiers(KModifier.OVERRIDE)
			.addTypeVariable(typeVariableT)
			.addParameter("handler", proxyHandlerClassName)
			.addParameter("classifier", classifierParamType)
			.returns(typeVariableT.copy(nullable = true))
			.addCode(whenBlock.build())
			.build()
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

		val companionObjectBuilder = TypeSpec.companionObjectBuilder()

		// Always use lazy mode with nullable vars for best performance and reduced class-loading cost.
		// Backing fields are stored in the companion object as private nullable vars.
		classDeclaration.getDeclaredFunctions().forEach { function ->
			val name = "_${function.simpleName.asString()}Descriptor"
			companionObjectBuilder.addProperty(
				PropertySpec.builder(name, FunctionDescriptor::class.asTypeName().copy(nullable = true))
					.mutable(true)
					.initializer("null")
					.addModifiers(KModifier.PRIVATE)
					.build()
			)
		}
		classDeclaration.getDeclaredProperties().forEach { property ->
			val name = "_${property.simpleName.asString()}Descriptor"
			companionObjectBuilder.addProperty(
				PropertySpec.builder(name, PropertyDescriptor::class.asTypeName().copy(nullable = true))
					.mutable(true)
					.initializer("null")
					.addModifiers(KModifier.PRIVATE)
					.build()
			)
		}

		val hiddenDeprecatedAnnotation = AnnotationSpec.builder(Deprecated::class)
			.addMember("message = %S", "Internal proxy implementation. Use factory function instead.")
			.addMember("level = %T.%L", DeprecationLevel::class, DeprecationLevel.HIDDEN.name)
			.build()

		val proxyClassSpec = TypeSpec.classBuilder(proxyClassName)
			.addSuperinterface(interfaceClassName)
			.primaryConstructor(primaryConstructor)
			.addProperty(handlerProperty)
			.addType(companionObjectBuilder.build())
			.addAnnotation(hiddenDeprecatedAnnotation)
			.apply {
				classDeclaration.getDeclaredProperties().forEach { property ->
					val propertyName = property.simpleName.asString()
					val propertyType = property.type.toTypeName()
					
					val backField = "_${propertyName}Descriptor"
					val initCode = generatePropertyDescriptorInitializer(property)
					// The "Check and Init" pattern requested for high performance
					val descriptorCode = CodeBlock.of("val descriptor = %N ?: %L.also { %N = it }", backField, initCode, backField)

					val getterSpec = FunSpec.getterBuilder()
						.addCode(descriptorCode)
						.addCode("\n")
						.addStatement("return handler.onGetProperty(descriptor) as %T", propertyType)
						.build()

					val propBuilder = PropertySpec.builder(propertyName, propertyType)
						.addModifiers(KModifier.OVERRIDE)
						.getter(getterSpec)

					if (property.isMutable) {
						val setterSpec = FunSpec.setterBuilder()
							.addParameter("v", propertyType)
							.addCode(descriptorCode)
							.addCode("\n")
							.addStatement("handler.onSetProperty(descriptor, v)")
							.build()

						propBuilder
							.mutable(true)
							.setter(setterSpec)
					}

					addProperty(propBuilder.build())
				}

				classDeclaration.getDeclaredFunctions().forEach { function ->
					val functionName = function.simpleName.asString()
					val returnType = function.returnType?.toTypeName() ?: UNIT

					val backField = "_${functionName}Descriptor"
					val initCode = generateFunctionDescriptorInitializer(function)
					// The "Check and Init" pattern requested for high performance
					val descriptorCode = CodeBlock.of("val descriptor = %N ?: %L.also { %N = it }", backField, initCode, backField)

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
						"return handler.onSuspendCall(descriptor, listOf(%L)) as %T"
					}
					else {
						"return handler.onCall(descriptor, listOf(%L)) as %T"
					}

					val funSpec = FunSpec.builder(functionName)
						.addModifiers(
							if (isSuspend) listOf(KModifier.OVERRIDE, KModifier.SUSPEND) else listOf(KModifier.OVERRIDE)
						)
						.returns(returnType)
						.addParameters(parameters)
						.addCode(descriptorCode)
						.addCode("\n")
						.addStatement(statement, argsCall, returnType)
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

	private fun generatePropertyDescriptorInitializer(
		property: KSPropertyDeclaration,
	): CodeBlock {
		val propertyName = property.simpleName.asString()
		val descriptorClassName = PropertyDescriptor::class.asTypeName()

		val typeKSType = property.type.resolve()
		val typeDescriptorCode = generateTypeDescriptorCode(typeKSType)

		val receiverTypeKSType = property.extensionReceiver?.toTypeName() as? KSType
		val receiverTypeCode = receiverTypeKSType?.let { generateTypeDescriptorCode(it) }

		return CodeBlock.builder().apply {
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
	}

	private fun generateFunctionDescriptorInitializer(
		function: KSFunctionDeclaration,
	): CodeBlock {
		val functionName = function.simpleName.asString()
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

		return CodeBlock.builder().apply {
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

}
