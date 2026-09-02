# KProxyable

**Compile-time Dynamic Proxies for Kotlin Multiplatform.**

`KProxyable` is a library that brings the power of dynamic proxies to the entire Kotlin Multiplatform (KMP) ecosystem. While the JVM has `java.lang.reflect.Proxy`, other platforms like JS, Native, and Wasm lack a built-in mechanism to intercept interface invocations at runtime without manual boilerplate.

This library uses **KSP (Kotlin Symbol Processing)** to generate proxy implementations at compile-time, providing a unified, type-safe API to intercept function calls, property accesses, and standard `Any` methods across all targets.

## Key Features

- 🌍 **Full KMP Support**: Works on JVM, JS, Native (iOS, macOS, Linux, Windows), and WasmJs.
- ⚡ **Zero Runtime Reflection**: Proxy logic is generated at compile-time for maximum performance.
- 🔄 **Unified Interception**: Transparently handle both synchronous and `suspend` function calls.
- 🛠️ **Property Support**: Intercept property getters and setters with ease.
- 🔍 **Any Method Interception**: Custom behavior for `equals`, `hashCode`, and `toString`.
- 🏷️ **Meta-Annotations**: Create custom annotations (e.g., `@HttpClient`) to automatically trigger proxy generation.
- 📦 **Cross-Module Discovery**: Seamlessly discover and use proxies defined in separate library modules; KProxyable automatically bundles them into your main application registry.

## Limitations

- 🔒 **Interface Visibility**: Only `public` or `internal` interfaces are supported. `private` interfaces cannot be proxied as the generated implementation needs to be able to see and implement the interface.

---

## Installation

### 1. Apply KSP and KProxyable Plugins
KProxyable requires the KSP plugin to be applied in your project. You must use the KSP version that matches your Kotlin version.

In your root `build.gradle.kts` (or application module):

```kotlin
plugins {
    // 1. Apply KSP with version matching your Kotlin version
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    
    // 2. Apply KProxyable
    id("io.github.elianfabian.kproxyable") version "1.0.5"
}
```

The KProxyable plugin automatically:
- Adds the `kproxyable-runtime` dependency to `commonMain`.
- Configures the `kproxyable-processor` for all Main and Test configurations (supporting cross-module discovery and unit tests).

### 2. Manual Dependency (Optional)
If you prefer to manage KSP and runtime dependencies manually:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.elianfabian:kproxyable-runtime:1.0.5")
    ksp("io.github.elianfabian:kproxyable-processor:1.0.5")
    kspTest("io.github.elianfabian:kproxyable-processor:1.0.5")
}
```

---

## Basic Usage

### 1. Define your Interface
Annotate any `public` or `internal` interface with `@KProxyable`:

```kotlin
import com.elianfabian.kproxyable.KProxyable

@KProxyable
interface MyService {
    fun doSomething(id: Int, name: String): String
    suspend fun fetchData(query: String): List<String>
    var isActive: Boolean
}
```

### 2. Implement a ProxyHandler
The `ProxyHandler` is responsible for intercepting all calls to the proxy instance:

```kotlin
class MyHandler : ProxyHandler {
    override fun onCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        println("Calling ${function.name} with $args")
        return "Intercepted result"
    }

    override suspend fun onSuspendCall(function: FunctionDescriptor, args: List<Any?>): Any? {
        return listOf("Async", "Result")
    }

    // Handle properties, equals, hashCode, and toString...
}
```

### 3. Create the Proxy
Use the `create` factory method to instantiate your intercepted interface:

```kotlin
// In JVM projects
val service = KProxyJvm.create<MyService>(MyHandler())

// In JS projects
val service = KProxyJs.create<MyService>(MyHandler())
```

---

## Platform-Specific Entry Points

KProxyable provides optimized entry points depending on your project type:

### JVM-only Projects
Use `KProxyJvm` for standard JVM or Android applications. It uses a lightweight `ServiceLoader` mechanism to discover generated registries.

### JS-only Projects
Use `KProxyJs` for Kotlin/JS projects. It leverages global scope hooks to link generated code without reflection.

### Kotlin Multiplatform (KMP) & Native
In a KMP project, you can define a shared entry point in `commonMain` using an `expect object`:

```kotlin
// commonMain
@KProxyRegistry
expect object MyProxyFactory : KProxyFactory

// usage
val service = MyProxyFactory.create<MyService>(MyHandler())
```
KSP will automatically generate the `actual` implementation in your platform-specific source sets, aggregating all `@KProxyable` interfaces found in your project and its dependencies.

---

## Advanced: Meta-Annotations

Instead of using `@KProxyable` everywhere, you can create your own domain-specific annotations:

```kotlin
@Target(AnnotationTarget.CLASS)
@KProxyable // This makes @MyClient a proxy trigger
annotation class MyClient

@MyClient
interface UserApi {
    fun getUser(id: String): User
}
```

---

## 🤖 About the Project

KProxyable is a showcase of **AI-Collaborative Engineering**. 

The implementation of this library, including its KSP processor and complex multi-platform CI/CD pipeline, was primarily driven by AI assistants working under human architectural guidance.
This project serves as an experiment in how modern AI tools can accelerate the development of professional-grade, specialized infrastructure for the Kotlin ecosystem.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
