package com.elianfabian.kproxyable

import com.google.devtools.ksp.symbol.KSTypeParameter
import com.squareup.kotlinpoet.ksp.toTypeVariableName
import com.squareup.kotlinpoet.ksp.toTypeParameterResolver
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
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
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
 * Version 1.1.5: Support for overloaded methods via unique descriptor names.
 */
public class KProxyableProcessor(
	private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

	private var processingTest = false
    private val genericCounts = mutableMapOf<ClassName, Int>()
    private var currentInterface: KSClassDeclaration? = null
    private fun effectiveProperty(p: KSPropertyDeclaration): KSType = if (p.parentDeclaration == currentInterface) p.type.resolve() else p.asMemberOf(currentInterface!!.asType(emptyList()))
    private fun effectiveReturn(f: KSFunctionDeclaration): KSType = if (f.parentDeclaration == currentInterface) f.returnType!!.resolve() else f.asMemberOf(currentInterface!!.asType(emptyList())).returnType!!
    private fun effectiveParameter(f: KSFunctionDeclaration, index: Int): KSType = if (f.parentDeclaration == currentInterface) f.parameters[index].type.resolve() else f.asMemberOf(currentInterface!!.asType(emptyList())).parameterTypes[index]!!
	private val types = TypeDescriptorGenerator()

	private val accumulatedInterfaceNames = mutableListOf<ClassName>()
	private val accumulatedOriginatingFiles = mutableListOf<KSFile>()
	private var registryClassName: ClassName? = null
	private var registryOriginatingFile: KSFile? = null


	override fun process(resolver: Resolver): List<KSAnnotated> {
		processingTest = processingTest || resolver.getAllFiles().any {
            Regex("[/\\\\]src[/\\\\][^/\\\\]*Test[/\\\\]").containsMatchIn(it.filePath)
        }
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

			genericCounts[classDeclaration.toClassName()] = classDeclaration.typeParameters.size
            try {
                generateProxyClass(classDeclaration)
            } catch (error: IllegalStateException) {
                environment.logger.error("Cannot generate proxy: ${error.message}", classDeclaration)
                return@forEach
            }
			accumulatedInterfaceNames.add(classDeclaration.toClassName())
			classDeclaration.containingFile?.let { accumulatedOriginatingFiles.add(it) }
		}

		return invalid
	}

	private fun getOption(key: String): String? {
		return environment.options[key] ?: environment.options.entries.find { it.key.endsWith(".$key") }?.value
	}

	override fun finish() {
		val moduleName = getOption("kproxyable.moduleName") ?: "unknown"
		val isTest = processingTest || getOption("kproxyable.isTest") == "true"
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

		// Support both direct classpath string and a pointer to a classpath file
		val classpathStr = getOption("kproxyable.fullClasspath") ?: getOption("kproxyable.classpath") ?: ""
        val classpathFile = getOption("kproxyable.classpathFile")?.let { File(it) }
        
		val classpathItems = if (classpathFile?.exists() == true) {
            classpathFile.readText().split(File.pathSeparator)
        } else {
            classpathStr.split(File.pathSeparator)
        }.filter { it.isNotBlank() }

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
		return discovered.filter { !it.endsWith(currentUniqueName) && !it.endsWith("_Test") }.toSet()
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
			whenBlock.addStatement("%T::class -> %T(handler) as T", interfaceClassName, if ((genericCounts[interfaceClassName] ?: 0) == 0) proxyClassName else proxyClassName.parameterizedBy(List(genericCounts.getValue(interfaceClassName)) { com.squareup.kotlinpoet.NOTHING }))
		}
		whenBlock.addStatement("else -> null").endControlFlow()
		return FunSpec.builder("findProxy").addModifiers(KModifier.OVERRIDE).addTypeVariable(TypeVariableName("T", ANY)).addParameter("handler", ProxyHandler::class.asTypeName()).addParameter("classifier", KClass::class.asClassName().parameterizedBy(TypeVariableName("T"))).returns(TypeVariableName("T").copy(nullable = true)).addCode(whenBlock.build()).build()
	}

	private fun generateProxyClass(classDeclaration: KSClassDeclaration) {
		val extensionProperty = classDeclaration.getAllProperties().firstOrNull { it.extensionReceiver != null }
		if (extensionProperty != null) {
			environment.logger.error("KProxyable does not support extension properties: the property handler has no receiver argument", extensionProperty)
			return
		}
		currentInterface = classDeclaration
        val packageName = classDeclaration.packageName.asString()
		val interfaceName = classDeclaration.simpleName.asString()
		val proxyClassName = "_${interfaceName}Proxy"

		val companionBuilder = TypeSpec.companionObjectBuilder()
        val allFunctions = classDeclaration.getAllFunctions().filter { Modifier.PRIVATE !in it.modifiers && !(
            (it.simpleName.asString() in setOf("hashCode", "toString") && it.parameters.isEmpty()) ||
            (it.simpleName.asString() == "equals" && it.parameters.size == 1 &&
                it.parameters.single().type.resolve().declaration.qualifiedName?.asString() == "kotlin.Any")
        ) }.toList()
		allFunctions.forEachIndexed { i, func -> 
            val descriptorName = if (allFunctions.count { it.simpleName.asString() == func.simpleName.asString() } > 1) {
                "_${func.simpleName.asString()}Descriptor_$i"
            } else {
                "_${func.simpleName.asString()}Descriptor"
            }
            companionBuilder.addProperty(PropertySpec.builder(descriptorName, FunctionDescriptor::class.asTypeName().copy(nullable = true)).mutable(true).initializer("null").addModifiers(KModifier.PRIVATE).build()) 
        }
		classDeclaration.getAllProperties().forEach { companionBuilder.addProperty(PropertySpec.builder("_${it.simpleName.asString()}Descriptor", PropertyDescriptor::class.asTypeName().copy(nullable = true)).mutable(true).initializer("null").addModifiers(KModifier.PRIVATE).build()) }

		val proxyClassSpec = TypeSpec.classBuilder(proxyClassName).addTypeVariables(classDeclaration.typeParameters.map { it.toTypeVariableName(classDeclaration.typeParameters.toTypeParameterResolver()) })
            .addSuperinterface(if (classDeclaration.typeParameters.isEmpty()) classDeclaration.toClassName() else classDeclaration.toClassName().parameterizedBy(classDeclaration.typeParameters.map { TypeVariableName(it.name.asString()) }))
			.primaryConstructor(FunSpec.constructorBuilder().addParameter("handler", ProxyHandler::class.asTypeName()).build())
			.addProperty(PropertySpec.builder("handler", ProxyHandler::class.asTypeName()).initializer("handler").addModifiers(KModifier.PRIVATE).build())
			.addType(companionBuilder.build())
			.apply {
				classDeclaration.getAllProperties().forEach { property ->
					val name = property.simpleName.asString()
					val type = if (property.type.element is com.google.devtools.ksp.symbol.KSDynamicReference) com.squareup.kotlinpoet.DYNAMIC else effectiveProperty(property).toTypeName(classDeclaration.typeParameters.toTypeParameterResolver())
					val descriptorName = "_${name}Descriptor"
					val initializer = generatePropertyDescriptorInitializer(property)
					
					val getterBuilder = FunSpec.getterBuilder()
						.addCode("val descriptor = $descriptorName ?: %L.also({\n    $descriptorName = it\n})\n", initializer)
						.apply { if (type == com.squareup.kotlinpoet.DYNAMIC) addStatement("return handler.onGetProperty(descriptor)") else addStatement("return handler.onGetProperty(descriptor) as %T", type) }

					val propBuilder = PropertySpec.builder(name, type).addModifiers(KModifier.OVERRIDE).getter(getterBuilder.build())

					if (property.isMutable) {
						propBuilder.mutable(true)
						propBuilder.setter(FunSpec.setterBuilder().addParameter("v", type)
							.addCode("val descriptor = $descriptorName ?: %L.also({\n    $descriptorName = it\n})\n", initializer)
							.addStatement("handler.onSetProperty(descriptor, v)").build())
					}
					addProperty(propBuilder.build())
				}
				allFunctions.forEachIndexed { i, function ->
					val name = function.simpleName.asString()
                    val descriptorName = if (allFunctions.count { it.simpleName.asString() == name } > 1) {
                        "_${name}Descriptor_$i"
                    } else {
                        "_${name}Descriptor"
                    }
					val typeResolver = function.typeParameters.toTypeParameterResolver(classDeclaration.typeParameters.toTypeParameterResolver())
                    val returnType = if (function.returnType!!.element is com.google.devtools.ksp.symbol.KSDynamicReference) com.squareup.kotlinpoet.DYNAMIC else if (function.returnType!!.element is com.google.devtools.ksp.symbol.KSDefNonNullReference) TypeVariableName((effectiveReturn(function).declaration as KSTypeParameter).name.asString() + " & Any") else effectiveReturn(function).toTypeName(typeResolver)
					val parameters = function.parameters.mapIndexed { index, it -> ParameterSpec.builder(it.name!!.asString(), (if (it.type.element is com.google.devtools.ksp.symbol.KSDynamicReference) com.squareup.kotlinpoet.DYNAMIC else if (it.type.element is com.google.devtools.ksp.symbol.KSDefNonNullReference) TypeVariableName((it.type.resolve().declaration as KSTypeParameter).name.asString() + " & Any") else effectiveParameter(function, index).toTypeName(typeResolver))).apply { if (it.isVararg) addModifiers(KModifier.VARARG) }.build() }
					val argsCall = (listOfNotNull(if (function.extensionReceiver != null) "this" else null) + function.parameters.map { it.name!!.asString() }).joinToString(", ")
					val isSuspend = Modifier.SUSPEND in function.modifiers
					val initializer = generateFunctionDescriptorInitializer(function)

					val funBuilder = FunSpec.builder(name)
						.addModifiers(if (isSuspend) listOf(KModifier.OVERRIDE, KModifier.SUSPEND) else listOf(KModifier.OVERRIDE))
						.returns(returnType)
						.apply { function.extensionReceiver?.let { receiver(it.toTypeName(typeResolver)) } }
                        .addParameters(parameters)
                        .addTypeVariables(function.typeParameters.map { it.toTypeVariableName(typeResolver) })
						.addCode("val descriptor = $descriptorName ?: (%L).also({\n    $descriptorName = it\n})\n", initializer)
					
					val callMethod = if (isSuspend) "onSuspendCall" else "onCall"
					if (returnType == com.squareup.kotlinpoet.DYNAMIC) funBuilder.addStatement("return handler.%L(descriptor, listOf(%L))", callMethod, argsCall)
                    else funBuilder.addStatement("return handler.%L(descriptor, listOf(%L)) as (%T)", callMethod, argsCall, returnType)

					addFunction(funBuilder.build())
				}
				addFunction(FunSpec.builder("equals").addModifiers(KModifier.OVERRIDE).addParameter("other", ANY.copy(nullable = true)).returns(Boolean::class).addStatement("return handler.onEquals(other)").build())
				addFunction(FunSpec.builder("hashCode").addModifiers(KModifier.OVERRIDE).returns(Int::class).addStatement("return handler.onHashCode()").build())
				addFunction(FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(String::class).addStatement("return handler.onToString()").build())
			}
			.build()

		FileSpec.builder(packageName, proxyClassName).addType(proxyClassSpec).build().writeTo(environment.codeGenerator, aggregating = false, originatingKSFiles = listOfNotNull(classDeclaration.containingFile))
	}

    // Compose each edge simultaneously, so Parent<T> through Mid<X> resolves T to List<String>.
    private fun inheritedSubstitutions(): CodeBlock {
        val all = linkedMapOf<KSTypeParameter, CodeBlock>()
        fun map(values: Map<KSTypeParameter, CodeBlock>): CodeBlock = CodeBlock.builder().add("mapOf(").apply {
            values.entries.forEachIndexed { index, (parameter, value) ->
                if (index > 0) add(", ")
                add("%L to %L", types.parameterId(parameter), value)
            }
        }.add(")").build()
        fun visit(declaration: KSClassDeclaration, environment: Map<KSTypeParameter, CodeBlock>, path: Set<KSClassDeclaration>) {
            if (declaration in path) return
            declaration.superTypes.forEach { reference ->
                val supertype = reference.resolve()
                val parent = supertype.declaration as? KSClassDeclaration ?: return@forEach
                val next = parent.typeParameters.zip(supertype.arguments).mapNotNull { (parameter, argument) ->
                    argument.type?.let { parameter to CodeBlock.of("%L.%M(%L)", types.reference(it),
                        MemberName("com.elianfabian.kproxyable", "substitute"), map(environment)) }
                }.toMap()
                all.putAll(next)
                visit(parent, next, path + declaration)
            }
        }
        visit(currentInterface!!, emptyMap(), emptySet())
        return map(all)
    }

    private fun ownerDescriptor(): CodeBlock = CodeBlock.of(
        "%T(classifier = %T::class, typeParameters = %L, annotations = %L)", InterfaceDescriptor::class,
        currentInterface!!.toClassName(), types.list(currentInterface!!.typeParameters.map(types::parameter)), types.annotations(currentInterface!!),
    )

    private fun generatePropertyDescriptorInitializer(property: KSPropertyDeclaration): CodeBlock = CodeBlock.of(
        "%T(name = %S, type = %L, isMutable = %L, annotations = %L, owner = %L).%M()",
        PropertyDescriptor::class, property.simpleName.asString(), types.reference(property.type, effectiveProperty(property)), property.isMutable,
        types.annotations(property), ownerDescriptor(), MemberName("com.elianfabian.kproxyable", "normalizeNullability"),
    )

    private fun generateFunctionDescriptorInitializer(function: KSFunctionDeclaration): CodeBlock = CodeBlock.of(
        "%T(name = %S, returnType = %L, parameters = %L, receiverType = %L, annotations = %L, isSuspend = %L, typeParameters = %L, owner = %L).%M(%L).%M()",
        FunctionDescriptor::class, function.simpleName.asString(), types.reference(function.returnType!!, effectiveReturn(function)),
        types.list(function.parameters.mapIndexed { index, p -> generateParameterDescriptorCode(p, effectiveParameter(function, index)) }),
        function.extensionReceiver?.resolve()?.let(types::type) ?: CodeBlock.of("null"), types.annotations(function),
        Modifier.SUSPEND in function.modifiers, types.list(function.typeParameters.map(types::parameter)), ownerDescriptor(),
        MemberName("com.elianfabian.kproxyable", "substitute"), inheritedSubstitutions(), MemberName("com.elianfabian.kproxyable", "normalizeNullability"),
    )

    private fun generateParameterDescriptorCode(param: KSValueParameter, type: KSType): CodeBlock = CodeBlock.of(
        "%T(name = %S, type = %L, isVararg = %L, hasDefault = %L, annotations = %L)",
        ParameterDescriptor::class, param.name?.asString().orEmpty(), types.reference(param.type, type),
        param.isVararg, param.hasDefault, types.annotations(param),
    )
}
