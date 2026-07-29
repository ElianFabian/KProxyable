package com.elianfabian.kproxyable

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Transforms calls like:
 *   KProxy.create<MyInterface>(handler)
 * Into direct constructor calls:
 *   _MyInterfaceProxy(handler)
 */
internal class CreateProxyFuncTransformer(
	private val pluginContext: IrPluginContext,
	private val debugLogger: DebugLogger,
) : IrElementTransformerVoidWithContext() {

	public companion object {
		private const val KPROXY_CREATE_FQ_NAME = "com.elianfabian.kproxyable.KProxy.create"

		fun errorTypeNotInterface(typeName: String): String =
			"KProxy.create<$typeName> is not supported. Type argument must be an interface."

		fun errorProxyNotFound(proxyName: String): String =
			"Class $proxyName not found. Did you annotate the interface with @KProxyable and apply KSP?"
	}

	@OptIn(UnsafeDuringIrConstructionAPI::class)
	override fun visitCall(expression: IrCall): IrExpression {
		val functionOwner = expression.symbol.owner
		val fqName = functionOwner.kotlinFqName.asString()

		// 1. Check if calling KProxy.create using full Qualified Name
		if (fqName != KPROXY_CREATE_FQ_NAME) {
			return super.visitCall(expression)
		}

		// 2. Extract type argument T from KProxy.create<T>(handler)
		if (expression.typeArgumentsCount == 0) {
			return super.visitCall(expression)
		}

		val typeArgument = expression.getTypeArgument(0)
			?: return super.visitCall(expression)

		if (!typeArgument.isInterface()) {
			throw IllegalStateException(errorTypeNotInterface(typeArgument.dumpKotlinLike()))
		}

		val classFqName = typeArgument.classFqName
			?: throw IllegalStateException(errorTypeNotInterface(typeArgument.dumpKotlinLike()))

		val packageName = classFqName.parent()
		val interfaceName = classFqName.shortName().asString()
		val proxyClassName = "_${interfaceName}Proxy"

		// 3. Find the generated _MyInterfaceProxy class symbol from KSP
		val classId = ClassId(packageName, Name.identifier(proxyClassName))
		val proxyClassSymbol = pluginContext.referenceClass(classId)
			?: throw IllegalStateException(errorProxyNotFound(proxyClassName))

		val proxyConstructor = proxyClassSymbol.constructors.firstOrNull()
			?: throw IllegalStateException("No constructor found for $proxyClassName")

		// 4. Extract 'handler' argument
		val handlerArgument = expression.getValueArgument(0)
			?: throw IllegalStateException("Expected 'handler' argument in KProxy.create()")

		debugLogger.log("Transformed KProxy.create<$interfaceName>() to $proxyClassName(handler)")

		// 5. Replace KProxy.create with _MyInterfaceProxy(handler) constructor call
		return IrConstructorCallImpl(
			startOffset = expression.startOffset,
			endOffset = expression.endOffset,
			type = proxyClassSymbol.owner.defaultType,
			symbol = proxyConstructor,
			typeArgumentsCount = 0,
			constructorTypeArgumentsCount = 0,
			valueArgumentsCount = 1,
			origin = null,
		).apply {
			putValueArgument(0, handlerArgument)
		}
	}
}
