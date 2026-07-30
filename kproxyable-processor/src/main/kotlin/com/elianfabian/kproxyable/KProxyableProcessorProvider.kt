package com.elianfabian.kproxyable

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

public class KProxyableProcessorProvider : SymbolProcessorProvider {

	override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
		return KProxyableProcessor(environment)
	}
}
