# KProxyable: Gradle Plugin Architecture

The `kproxyable-gradle-plugin` is the central orchestrator that automates the setup of KSP and dependencies across JVM, JS, and Multiplatform projects. This document details its internal mechanisms and logic.

---

## 1. Core Responsibilities

The plugin manages the following aspects of a project:
1.  **Plugin Orchestration**: Automatically applies `com.google.devtools.ksp`.
2.  **Dependency Injection**: Adds `kproxyable-runtime` to the project's source sets and `kproxyable-processor` to the KSP configurations.
3.  **KSP Configuration**: Passes critical metadata to the processor via compiler arguments.
4.  **Klib Support**: Fixes resource bundling for JS and Wasm targets.

---

## 2. Dynamic Target Configuration

The plugin is "reactive" and adapts its behavior based on which other Kotlin plugins are applied to the project.

### Kotlin Multiplatform (KMP)
If `org.jetbrains.kotlin.multiplatform` is detected:
- Adds `kproxyable-runtime` to `commonMain`.
- Configures KSP for **every target** (e.g., `kspJvm`, `kspJs`, `kspLinuxX64`).
- Handles the special `metadata` compilation for cross-platform symbol resolution.

### JVM-Only and JS-Only Projects
If a project is platform-specific (non-KMP):
- Simplifies dependency setup using standard `implementation` and `ksp` configurations.
- Handles target-specific KSP task naming (e.g., `kspKotlinJs`).

---

## 3. Advanced Mechanisms

### Application Detection (`isApp`)
To avoid duplicate Master Registries, the plugin must identify which module is the "final" project.
- **Method**: Uses a lazy `Provider` and reflection to check for:
    - Android Application, Gradle Application, or Kotlin/JVM Application plugins.
    - **JS/KMP Executables**: Scans the Kotlin extension's targets and binaries for any defined `Executable`.
- **Impact**: Sets the `kproxyable.isApp` KSP argument, which tells the processor to generate the `KProxyJvmImpl` or `KProxyJsImpl` entry points.

### Lazy Classpath Resolution
The processor needs a list of all dependencies to scan for "breadcrumbs" (metadata files).
- **The Problem**: Resolving the classpath during Gradle's configuration phase causes "Configuration already resolved" errors.
- **The Solution**: Uses a `project.provider` to lazily resolve the paths only during task execution. It intelligently scans relevant configurations like `jsCompileClasspath`, `jvmCompileClasspath`, and transitive placeholders.

### Klib Resource Bundle Fix (JS/Wasm)
In Kotlin/JS, KSP-generated resources (like `META-INF/services`) are often missed by the default packaging tasks.
- **The Fix**: 
    1.  Locates the target-specific KSP resource output folder.
    2.  Adds it as a source directory to the compilation's resources.
    3.  Configures `ProcessResources` and `compileKotlin` tasks to explicitly depend on the corresponding KSP task.

---

## 4. KSP Compiler Arguments

| Argument | Value Type | Description |
| :--- | :--- | :--- |
| `kproxyable.moduleName` | String | A sanitized, unique identifier for the module's registry. |
| `kproxyable.isApp` | Boolean (String) | Triggers the generation of the platform Master Registry. |
| `kproxyable.classpath` | String | Path-separated list of all dependencies for breadcrumb discovery. |

---

## 5. Development Mode

The plugin includes logic to detect if it is running within the KProxyable repository itself.
- **Internal**: Uses `project(":kproxyable-...")` for immediate compilation feedback.
- **External**: Resolves dependencies using the project's group and version for published consumers.
