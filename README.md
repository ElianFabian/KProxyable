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

---

## Installation

### 1. Apply KSP and KProxyable Plugins

KProxyable requires the `kotlin("multiplatform")` plugin to be applied, even for single-target
projects.

In your root `build.gradle.kts`:

```kotlin
plugins {
	// 1. Apply KSP matching your Kotlin version
	id("com.google.devtools.ksp") version "2.0.21-1.0.28"

	// 2. Apply KProxyable
	id("io.github.elianfabian.kproxyable") version "1.1.0"
}
```

### 2. Single-Target Projects

If you are building an application that only targets one platform (e.g., JVM-only or JS-only), you
must still use the multiplatform plugin to enable the `expect/actual` mechanism:

```kotlin
// build.gradle.kts
plugins {
	kotlin("multiplatform")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	jvm() // or js(IR), or androidTarget()

	sourceSets {
		commonMain.dependencies {
			// Your dependencies here
		}
	}
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
linking all discovered proxies.

### 3. Implement a ProxyHandler

The `ProxyHandler` intercepts all calls to the proxy instance. You must implement all its methods:

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
val service = KProxy.create<MyService>(MyHandler())
```

---

## Advanced: JVM / Android Applications

When using the Gradle `application` plugin or building an Android APK, the standard "run" tasks
might not automatically include KSP-generated classes in their classpath. You can fix this with a
simple helper in your `build.gradle.kts`:

```kotlin
// help the application plugin find KMP/KSP outputs
tasks.withType<JavaExec>().configureEach {
	val jvmTarget =
		kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
	classpath += jvmTarget.compilations.getByName("main").output.allOutputs
}
```

---

## 🤖 About the Project

KProxyable is a showcase of **AI-Collaborative Engineering**.
The library's specialized infrastructure, including its KSP processor and multi-platform linkage
system, was primarily implemented by AI assistants under human architectural guidance.

## License

MIT License. See [LICENSE](LICENSE) for details.
