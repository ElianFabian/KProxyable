# KProxyable: Generated Code Architecture

This document details the patterns and naming conventions used by the KProxyable KSP processor.
The generated code is designed for high-performance static linkage with **zero runtime reflection**.

---

## 1. Proxy Implementation Classes

For every interface annotated with `@KProxyable`, the processor generates a concrete implementation
named `_InterfaceNameProxy`.

### High-Performance Lazy Descriptors

To minimize class-loading overhead, member descriptors (metadata) are initialized only upon first access.

```kotlin
public class _MyServiceProxy(private val handler: ProxyHandler) : MyService {
    override fun greet(name: String): String {
        val descriptor = _greetDescriptor ?: FunctionDescriptor(...)
            .also { _greetDescriptor = it }
        
        // Zero-reflection delegation
        return handler.onCall(descriptor, listOf(name)) as String
    }

    public companion object {
        private var _greetDescriptor: FunctionDescriptor? = null
    }
}
```

---

## 2. Truly Unified Static Linkage

KProxyable uses a multi-layered static discovery system that works identically on all targets 
(JVM, JS, WasmJs, Native).

### Layer 1: Module Registries

Generated in every module containing `@KProxyable` interfaces.

- **Naming**: `com.elianfabian.kproxyable.generated.KProxyRegistry_<moduleName>`.
- **Logic**: Contains a direct `when` block mapping interfaces to their `_Proxy` implementations.

### Layer 2: Master Linkage (`KProxyActual.kt`)

When a user defines an `expect object KProxy` annotated with `@KProxyRegistry`, KSP generates the
`actual` implementation.

This actual object chains the local module registry and all discovered dependency registries
using an Elvis (`?:`) chain.

```kotlin
public actual object KProxy : KProxyFactory {
  override fun <T : Any> findProxy(handler: ProxyHandler, classifier: KClass<T>): T? =
      KProxyRegistry_app.findProxy(handler, classifier)
   ?: KProxyRegistry_common_library.findProxy(handler, classifier)
   ?: KProxyRegistry_other_feature.findProxy(handler, classifier)
}
```

---

## 3. The Discovery Engine

Proxies are found across module boundaries without manual registration or runtime reflection.

### Breadcrumbs (Metadata)

When a module registry is generated, a "breadcrumb" file is created in `META-INF/services/`.

### Build-Time Scanning

The KSP processor scans the compilation classpath (JARs and Klibs) for these breadcrumbs. It
automatically identifies every KProxyable-enabled module you depend on and includes its registry
in the `actual KProxy` chain.

---

## 4. Test Source Set Support

KProxyable treats `Main` and `Test` source sets with specific awareness:

- **Local Linkage**: In a test source set, the generated registry links both the `Main` proxies
  and any internal interfaces marked with `@KProxyable` inside the test sources.
- **Naming**: Test registries are suffixed with `_Test` (e.g., `KProxyRegistry_core_Test`).
