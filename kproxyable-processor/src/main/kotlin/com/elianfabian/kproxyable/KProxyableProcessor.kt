package com.elianfabian.kproxyable

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File
import java.util.zip.ZipFile
import kotlin.reflect.KClass

/**
 * Truly Unified KMP Symbol Processor for KProxyable.
 * Version 1.1.9: Robust cross-module discovery and platform safety.
 */
public class KProxyableProcessor(
	private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

	private val accumulatedInterfaceNames = mutableListOf<ClassName>()
	private val accumulatedOriginatingFiles = mutableListOf<KSFile>()
	private var registryClassName: ClassName? = null
	private var registryOriginatingFile: KSFile? = null


	override fun process(resolver: Resolver): List<KSAnnotated> {
		val proxyableAnnotationName = KProxyable::class.qualifiedName ?: return emptyList()
		val registryAnnotationName = KProxyRegistry::class.qualifiedName ?: return emptyList()

		// 1. Find user's master registry
		resolver.getSymbolsWithAnnotation(registryAnnotationName)
			.filterIsInstance<KSClassDeclaration>()
			.firstOrNull { it.classKind == ClassKind.OBJECT && it.isExpect }?.let {
				registryClassName = it.toClassName()
				registryOriginatingFile = it.containingFile
			}

		// 2. Find interfaces
		val targetSymbols = resolver.getSymbolsWithAnnotation(proxyableAnnotationName)
			.filterIsInstance<KSClassDeclaration>()
			.toList()

		val (valid, invalid) = targetSymbols.partition { it.validate() }

		valid.forEach { classDeclaration ->
			if (classDeclaration.classKind != ClassKind.INTERFACE) {
				environment.logger.error("@KProxyable can only be applied to interfaces", classDeclaration)
				return@forEach
			}

			generateProxyClass(classDeclaration)
			accumulatedInterfaceNames.add(classDeclaration.toClassName())
			classDeclaration.containingFile?.let { accumulatedOriginatingFiles.add(it) }
		}

		return invalid
	}

	private fun getOption(key: String): String? {
		return environment.options[key] ?: environment.options["plugin:com.google.devtools.ksp.symbol-processing:$key"] ?: environment.options.entries.find { it.key.endsWith(".$key") }?.value
	}

	override fun finish() {
		val moduleName = getOption("kproxyable.moduleName") ?: "unknown"
		val isTest = getOption("kproxyable.isTest") == "true"
		val uniqueRegistryName = if (isTest) "KProxyRegistry_${moduleName}_Test" else "KProxyRegistry_$moduleName"

		if (accumulatedInterfaceNames.isNotEmpty()) {
			generateModuleRegistry(uniqueRegistryName, accumulatedInterfaceNames, accumulatedOriginatingFiles)
			generateBreadcrumb(uniqueRegistryName, accumulatedOriginatingFiles)
		}

		registryClassName?.let {
			generateCompositeActualRegistry(it, accumulatedInterfaceNames, accumulatedOriginatingFiles, registryOriginatingFile, moduleName, isTest)
		}
	}

	private fun generateModuleRegistry(uniqueRegistryName: String, interfaceNames: List<ClassName>, originatingFiles: List<KSFile>) {
		val packageName = "com.elianfabian.kproxyable.generated"
		val findFunSpec = generateFindProxyFunction(interfaceNames)

		val typeSpec = TypeSpec.objectBuilder(uniqueRegistryName)
			.addModifiers(KModifier.PUBLIC)
			.addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(findFunSpec)
			.build()

		FileSpec.builder(packageName, uniqueRegistryName)
			.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "DEPRECATION").addMember("%S", "UNCHECKED_CAST").useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).build())
			.addType(typeSpec)
			.build()
			.writeTo(environment.codeGenerator, aggregating = true, originatingKSFiles = originatingFiles.distinct())
	}

	private fun generateBreadcrumb(uniqueRegistryName: String, originatingFiles: List<KSFile>) {
		val fqn = "com.elianfabian.kproxyable.generated.$uniqueRegistryName"
		environment.codeGenerator.createNewFile(Dependencies(true, *originatingFiles.toTypedArray()), "META-INF.services", "com.elianfabian.kproxyable.KProxyFactory", "").use { it.write(fqn.toByteArray()) }
	}

	private fun discoverRegistries(currentUniqueName: String): Set<String> {
		val discovered = mutableSetOf<String>()
		val serviceFileName = "com.elianfabian.kproxyable.KProxyFactory"

		val classpathStr = getOption("kproxyable.fullClasspath") ?: getOption("kproxyable.classpath") ?: ""
		val classpathItems = classpathStr.split(File.pathSeparator).filter { it.isNotBlank() }

		classpathItems.forEach { item ->
			val file = File(item)
			if (!file.exists()) return@forEach
			if (file.isDirectory) {
				file.walkTopDown().maxDepth(15).filter { it.isFile && (it.name == serviceFileName || it.path.contains("META-INF/services/$serviceFileName")) }.forEach {
					it.useLines { lines -> discovered.addAll(lines.map { l -> l.trim() }.filter { l -> l.isNotEmpty() && !l.startsWith("#") }) }
				}
			}
			else if (file.extension == "jar" || file.extension == "klib") {
				try {
					ZipFile(file).use { zip ->
						zip.entries().asSequence().filter { !it.isDirectory && (it.name.endsWith("/$serviceFileName") || it.name == serviceFileName) }.forEach { entry ->
							zip.getInputStream(entry).bufferedReader().useLines { lines ->
								discovered.addAll(lines.map { l -> l.trim() }.filter { l -> l.isNotEmpty() && !l.startsWith("#") })
							}
						}
					}
				}
				catch (_: Exception) {
				}
			}
		}
		return discovered.filter { !it.endsWith(currentUniqueName) }.toSet()
	}

	private fun generateCompositeActualRegistry(registryName: ClassName, interfaceNames: List<ClassName>, originatingFiles: List<KSFile>, registryOriginatingFile: KSFile?, moduleName: String, isTest: Boolean) {
		val currentUniqueName = if (isTest) "KProxyRegistry_${moduleName}_Test" else "KProxyRegistry_$moduleName"
		val discovered = discoverRegistries(currentUniqueName).toMutableSet()

		val localFqn = "com.elianfabian.kproxyable.generated.$currentUniqueName"
		if (interfaceNames.isNotEmpty()) discovered.add(localFqn)

		if (isTest) {
			discovered.add("com.elianfabian.kproxyable.generated.KProxyRegistry_$moduleName")
		}

		val codeBlock = CodeBlock.builder()
		codeBlock.add("return ")

		val allRegistries = discovered.distinct().toList()
		if (allRegistries.isEmpty()) {
			codeBlock.addStatement("null")
		}
		else {
			allRegistries.forEachIndexed { i, fqn ->
				codeBlock.add("%T.findProxy(handler, classifier)", ClassName.bestGuess(fqn))
				if (i < allRegistries.size - 1) codeBlock.add("\n ?: ")
			}
			codeBlock.add("\n")
		}

		val actualObjectSpec = TypeSpec.objectBuilder(registryName.simpleName)
			.addModifiers(KModifier.ACTUAL).addAnnotation(KProxyRegistry::class).addSuperinterface(KProxyFactory::class.asTypeName())
			.addFunction(FunSpec.builder("findProxy").addModifiers(KModifier.OVERRIDE).addTypeVariable(TypeVariableName("T", ANY)).addParameter("handler", ProxyHandler::class.asTypeName()).addParameter("classifier", KClass::class.asClassName().parameterizedBy(TypeVariableName("T"))).returns(TypeVariableName("T").copy(nullable = true)).addCode(codeBlock.build()).build())
			.build()

		FileSpec.builder(registryName.packageName, "${registryName.simpleName}Actual")
			.addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "DEPRECATION").addMember("%S", "UNCHECKED_CAST").useSiteTarget(AnnotationSpec.UseSiteTarget.FILE).build())
			.addType(actualObjectSpec).build().writeTo(environment.codeGenerator, aggregating = true, originatingKSFiles = (originatingFiles + listOfNotNull(registryOriginatingFile)).distinct())
	}

	private fun generateFindProxyFunction(interfaceNames: List<ClassName>): FunSpec {
		val whenBlock = CodeBlock.builder().beginControlFlow("return when (classifier)")
		interfaceNames.distinct().forEach { interfaceClassName ->
			val proxyClassName = ClassName(interfaceClassName.packageName, "_${interfaceClassName.simpleName}Proxy")
			whenBlock.addStatement("%T::class -> %T(handler) as T", interfaceClassName, proxyClassName)
		}
		whenBlock.addStatement("else -> null").endControlFlow()
		return FunSpec.builder("findProxy").addModifiers(KModifier.OVERRIDE).addTypeVariable(TypeVariableName("T", ANY)).addParameter("handler", ProxyHandler::class.asTypeName()).addParameter("classifier", KClass::class.asClassName().parameterizedBy(TypeVariableName("T"))).returns(TypeVariableName("T").copy(nullable = true)).addCode(whenBlock.build()).build()
	}

	private fun generateProxyClass(classDeclaration: KSClassDeclaration) {
		val packageName = classDeclaration.packageName.asString()
		val interfaceName = classDeclaration.simpleName.asString()
		val proxyClassName = "_${interfaceName}Proxy"

		val companionBuilder = TypeSpec.companionObjectBuilder()
		classDeclaration.getDeclaredFunctions().forEach { companionBuilder.addProperty(PropertySpec.builder("_${it.simpleName.asString()}Descriptor", FunctionDescriptor::class.asTypeName().copy(nullable = true)).mutable(true).initializer("null").addModifiers(KModifier.PRIVATE).build()) }
		classDeclaration.getDeclaredProperties().forEach { companionBuilder.addProperty(PropertySpec.builder("_${it.simpleName.asString()}Descriptor", PropertyDescriptor::class.asTypeName().copy(nullable = true)).mutable(true).initializer("null").addModifiers(KModifier.PRIVATE).build()) }

		val proxyClassSpec = TypeSpec.classBuilder(proxyClassName).addSuperinterface(classDeclaration.toClassName())
			.primaryConstructor(FunSpec.constructorBuilder().addParameter("handler", ProxyHandler::class.asTypeName()).build())
			.addProperty(PropertySpec.builder("handler", ProxyHandler::class.asTypeName()).initializer("handler").addModifiers(KModifier.PRIVATE).build())
			.addType(companionBuilder.build())
			.apply {
				classDeclaration.getDeclaredProperties().forEach { property ->
					val name = property.simpleName.asString()
					val type = property.type.toTypeName()
					val descriptorCode = CodeBlock.of("val descriptor = _${name}Descriptor ?: %L.also { _${name}Descriptor = it }", generatePropertyDescriptorInitializer(property))

					val getterBuilder = FunSpec.getterBuilder().addCode(descriptorCode).addCode("\n")
					getterBuilder.addStatement("return handler.onGetProperty(descriptor) as %T", type)

					val propBuilder = PropertySpec.builder(name, type).addModifiers(KModifier.OVERRIDE).getter(getterBuilder.build())

					if (property.isMutable) {
						propBuilder.mutable(true)
						propBuilder.setter(FunSpec.setterBuilder().addParameter("v", type).addCode(descriptorCode).addCode("\n").addStatement("handler.onSetProperty(descriptor, v)").build())
					}
					addProperty(propBuilder.build())
				}
				classDeclaration.getDeclaredFunctions().forEach { function ->
					val name = function.simpleName.asString()
					val returnType = function.returnType?.toTypeName() ?: UNIT
					val parameters = function.parameters.map { ParameterSpec.builder(it.name!!.asString(), it.type.toTypeName()).build() }
					val argsCall = function.parameters.joinToString(", ") { it.name!!.asString() }
					val isSuspend = Modifier.SUSPEND in function.modifiers
					val descriptorCode = CodeBlock.of("val descriptor = _${name}Descriptor ?: %L.also { _${name}Descriptor = it }", generateFunctionDescriptorInitializer(function))

					val funBuilder = FunSpec.builder(name).addModifiers(if (isSuspend) listOf(KModifier.OVERRIDE, KModifier.SUSPEND) else listOf(KModifier.OVERRIDE)).returns(returnType).addParameters(parameters)
						.addCode(descriptorCode)
						.addCode("\n")
					
					val callMethod = if (isSuspend) "onSuspendCall" else "onCall"
					funBuilder.addStatement("return handler.%L(descriptor, listOf(%L)) as %T", callMethod, argsCall, returnType)

					addFunction(funBuilder.build())
				}
				addFunction(FunSpec.builder("equals").addModifiers(KModifier.OVERRIDE).addParameter("other", ANY.copy(nullable = true)).returns(Boolean::class).addStatement("return handler.onEquals(other)").build())
				addFunction(FunSpec.builder("hashCode").addModifiers(KModifier.OVERRIDE).returns(Int::class).addStatement("return handler.onHashCode()").build())
				addFunction(FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(String::class).addStatement("return handler.onToString()").build())
			}
			.build()

		FileSpec.builder(packageName, proxyClassName).addType(proxyClassSpec).build().writeTo(environment.codeGenerator, aggregating = false, originatingKSFiles = listOfNotNull(classDeclaration.containingFile))
	}

	private fun generatePropertyDescriptorInitializer(property: KSPropertyDeclaration): CodeBlock = CodeBlock.of("%T(name = %S, type = %L, isMutable = %L)", PropertyDescriptor::class.asTypeName(), property.simpleName.asString(), generateTypeDescriptorCode(property.type.resolve()), property.isMutable)
	private fun generateFunctionDescriptorInitializer(function: KSFunctionDeclaration): CodeBlock {
		val paramCodes = function.parameters.map { generateParameterDescriptorCode(it) }
		val paramsList = if (paramCodes.isEmpty()) CodeBlock.of("emptyList()") else CodeBlock.builder().add("listOf(").apply { paramCodes.forEachIndexed { i, p -> add("%L", p); if (i < paramCodes.size - 1) add(", ") } }.add(")").build()
		return CodeBlock.of("%T(name = %S, returnType = %L, parameters = %L)", FunctionDescriptor::class.asTypeName(), function.simpleName.asString(), function.returnType?.resolve()?.let { generateTypeDescriptorCode(it) } ?: CodeBlock.of("%T(classifier = %T::class)", TypeDescriptor::class.asTypeName(), UNIT), paramsList)
	}

	private fun generateTypeDescriptorCode(ksType: KSType): CodeBlock {
		val typeArgs = ksType.arguments.mapNotNull { it.type?.resolve()?.let { t -> generateTypeDescriptorCode(t) } }
		val argsList = if (typeArgs.isEmpty()) CodeBlock.of("emptyList()") else CodeBlock.builder().add("listOf(").apply { typeArgs.forEachIndexed { i, a -> add("%L", a); if (i < typeArgs.size - 1) add(", ") } }.add(")").build()
		return CodeBlock.of("%T(classifier = %T::class, isNullable = %L, typeArguments = %L)", TypeDescriptor::class.asTypeName(), (ksType.declaration as? KSClassDeclaration)?.toClassName() ?: ANY, ksType.isMarkedNullable, argsList)
	}

	private fun generateParameterDescriptorCode(param: KSValueParameter): CodeBlock = CodeBlock.of("%T(name = %S, type = %L, isVararg = %L, hasDefault = %L)", ParameterDescriptor::class.asTypeName(), param.name?.asString().orEmpty(), generateTypeDescriptorCode(param.type.resolve()), param.isVararg, param.hasDefault)
}
