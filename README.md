# KProxyable

**Compile-time Dynamic Proxies for Kotlin Multiplatform.**

`KProxyable` brings the power of dynamic proxies to the entire Kotlin Multiplatform (KMP) ecosystem.
Intercept interface invocations at runtime with zero reflection, maximum performance, and full type
safety.

This library uses **KSP (Kotlin Symbol Processing)** to generate proxy implementations and registry
linkages at compile-time.

## Key Features

- 🌍 **Pure KMP Architecture**: Works seamlessly on JVM, JS, Native (iOS, Android, Desktop), and
  WasmJs.
- ⚡ **Zero Runtime Reflection**: Proxy logic and factory linkage are generated at compile-time.
- 🔄 **Unified Interception**: Handle both synchronous and `suspend` function calls.
- 🛠️ **Property Interception**: Intercept property getters and setters.
- 🔍 **Any Method Interception**: Custom behavior for `equals`, `hashCode`, and `toString`.
- 📦 **Cross-Module Discovery**: Automatically aggregates proxies from separate library modules into
  your main application.
- 🚀 **Kotlin 2.x Lineage**: Fully compatible with Kotlin 2.0, 2.1, 2.2, and 2.4+.

---

## Installation

### 1. Apply Plugins

KProxyable requires the KSP plugin. In your root `build.gradle.kts`:

```kotlin
plugins {
    // 1. Apply KSP matching your Kotlin version
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false

    // 2. Apply KProxyable
    id("io.github.elianfabian.kproxyable") version "1.1.1" apply false
}
```

### 2. Module Setup (App or Library)

The plugin works **automagically** for Multiplatform, Pure JVM, and Pure JS projects. 
Manual dependency blocks for the processor or runtime are no longer required.

#### For Multiplatform Projects
```kotlin
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
    id("io.github.elianfabian.kproxyable")
}

kotlin {
    jvm()
    iosArm64()
    // ... other targets
}
```

#### For Pure JVM Projects
```kotlin
plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("io.github.elianfabian.kproxyable")
}
```

---

## Basic Usage

### 1. Define your Interface

Annotate any `public` or `internal` interface with `@KProxyable`:

```kotlin
@KProxyable
interface MyService {
    fun doSomething(id: Int): String
    suspend fun fetchData(): List<String>
    var isActive: Boolean
}
```

### 2. Create a Shared Registry

Define an `expect object` in `commonMain` (or `commonTest`) annotated with `@KProxyRegistry`. This
is your entry point for creating proxies.

```kotlin
// src/commonMain/kotlin/...
import com.elianfabian.kproxyable.KProxyFactory
import com.elianfabian.kproxyable.KProxyRegistry

@KProxyRegistry
expect object KProxy : KProxyFactory
```

KProxyable will automatically generate the `actual` implementation in all your target source sets,
linking all discovered proxies from the current module and all dependencies.

### 3. Implement a ProxyHandler

The `ProxyHandler` intercepts all calls to the proxy instance:

```kotlin
class MyHandler : ProxyHandler {
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        println("Calling ${function.name} with $args")
        return "Intercepted result"
    }

    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        return listOf("Async", "Result")
    }

    override fun onGetProperty(property: PropertyDescriptor): Any? {
        return if (property.name == "isActive") true else null
    }

    override fun onSetProperty(property: PropertyDescriptor, value: Any?) {
        println("Setting ${property.name} to $value")
    }

    override fun onEquals(other: Any?): Boolean = this === other
    override fun onHashCode(): Int = 42
    override fun onToString(): String = "MyProxyHandler"
}
```

### 4. Create the Proxy

Use the `create` extension method on your registry:

```kotlin
import com.elianfabian.kproxyable.create

val service = KProxy.create<MyService>(MyHandler())
```

---

## Developer Matrix Testing

To ensure the library remains stable across the rapidly evolving Kotlin 2.x ecosystem, we've included 
automation scripts in the `scratch/` folder.

You can test a specific version:
```powershell
powershell -File scratch/run_matrix.ps1 -Version 2.1.0
```

Or run the full verified suite:
```powershell
powershell -File scratch/test_all_versions.ps1
```

---

## 🤖 About the Project

KProxyable is a showcase of **AI-Collaborative Engineering**.
The library's specialized infrastructure, including its KSP processor and multi-platform linkage
system, was primarily implemented by AI assistants under human architectural guidance.

## License

MIT License. See [LICENSE](LICENSE) for details.
