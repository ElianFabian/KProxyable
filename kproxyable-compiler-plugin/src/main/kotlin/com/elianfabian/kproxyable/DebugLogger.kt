package com.elianfabian.kproxyable

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression

internal data class DebugLogger(
	val debug: Boolean,
	val messageCollector: MessageCollector
) {
	fun log(message: String) {
		if (debug) {
			messageCollector.report(CompilerMessageSeverity.INFO, message)
		}
	}
}

internal class KProxyableIrGenerationExtension constructor(
	val debugLogger: DebugLogger,
) : IrGenerationExtension {
	override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
		debugLogger.log("=== [DEBUG] IR Generation started for module: ${moduleFragment.name} ===")
		moduleFragment.transform(ElementTransformer(pluginContext, debugLogger), null)
	}
}

internal class ElementTransformer(
	private val pluginContext: IrPluginContext,
	private val debugLogger: DebugLogger
) : IrElementTransformerVoidWithContext() {

	override fun visitValueParameterNew(declaration: IrValueParameter): IrStatement {
		declaration.transform(CreateProxyFuncTransformer(pluginContext, debugLogger), null)
		return super.visitValueParameterNew(declaration)
	}

	override fun visitPropertyNew(declaration: IrProperty): IrStatement {
		declaration.transform(CreateProxyFuncTransformer(pluginContext, debugLogger), null)
		return super.visitPropertyNew(declaration)
	}

	override fun visitCall(expression: IrCall): IrExpression {
		expression.transform(CreateProxyFuncTransformer(pluginContext, debugLogger), null)
		return super.visitCall(expression)
	}

	override fun visitVariable(declaration: IrVariable): IrStatement {
		declaration.transform(CreateProxyFuncTransformer(pluginContext, debugLogger), null)
		return super.visitVariable(declaration)
	}

	override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
		expression.transform(CreateProxyFuncTransformer(pluginContext, debugLogger), null)
		return super.visitFunctionExpression(expression)
	}
}
