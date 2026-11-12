package com.elianfabian.kproxyable.probe

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/** Records unmodified KSP symbols so contract normalization cannot hide compiler behavior changes. */
public class FunctionSymbolProbeProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor = FunctionSymbolProbe(environment.codeGenerator)
}

private class FunctionSymbolProbe(private val generator: CodeGenerator) : SymbolProcessor {
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        val declaration = resolver.getClassDeclarationByName(resolver.getKSNameFromString(
            "com.elianfabian.kproxyable.sample.FunctionTypeProbe",
        )) ?: return emptyList()
        val functions = declaration.declarations.filterIsInstance<KSFunctionDeclaration>().associateBy { it.simpleName.asString() }
        fun type(name: String): KSType {
            val function = functions.getValue(name)
            return (function.parameters.firstOrNull()?.type ?: function.returnType!!).resolve()
        }
        fun KSType.parameterName(): String = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.ParameterName"
        }?.arguments?.single()?.value as? String ?: "absent"
        fun KSType.argument(index: Int): KSType = arguments[index].type!!.resolve()
        val facts = linkedMapOf<String, String>()
        for (name in listOf("arrow", "explicitFunction", "ordinaryFunction", "reflectedFunction", "reflectedSuspendFunction", "lookalike", "arity23", "suspendArity23")) {
            val value = type(name)
            facts["$name.declaration"] = value.declaration.qualifiedName!!.asString()
            facts["$name.isFunctionType"] = value.isFunctionType.toString()
            facts["$name.isSuspendFunctionType"] = value.isSuspendFunctionType.toString()
            facts["$name.arguments"] = value.arguments.size.toString()
        }
        facts["redundant.variances"] = type("redundant").arguments.joinToString(",") { it.variance.name }
        facts["manualReceiver.extension"] = type("manualReceiver").annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == "kotlin.ExtensionFunctionType"
        }.toString()
        facts["named.input"] = type("named").argument(0).parameterName()
        facts["markedReceiver.receiver"] = type("markedReceiver").argument(0).parameterName()
        facts["markedReceiver.input"] = type("markedReceiver").argument(1).parameterName()
        facts["nestedName.input"] = type("nestedName").argument(0).parameterName()
        facts["nestedName.element"] = type("nestedName").argument(0).argument(0).parameterName()
        facts["nestedFunction.outer"] = type("nestedFunction").argument(0).parameterName()
        facts["nestedFunction.inner"] = type("nestedFunction").argument(1).argument(0).parameterName()
        facts["namedList.element"] = type("namedList").argument(0).parameterName()
        generator.createNewFile(
            Dependencies(false, declaration.containingFile!!),
            "com.elianfabian.kproxyable.sample.probe", "RawKspFunctionFacts",
        ).bufferedWriter().use { writer ->
            writer.appendLine("package com.elianfabian.kproxyable.sample.probe")
            writer.appendLine("internal actual val rawKspFunctionFacts: Map<String, String> = mapOf(")
            for ((key, value) in facts) writer.appendLine("    \"$key\" to \"$value\",")
            writer.appendLine(")")
        }
        generated = true
        return emptyList()
    }
}
