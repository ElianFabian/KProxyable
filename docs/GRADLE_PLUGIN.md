# KProxyable: Gradle Plugin Architecture

The `kproxyable-gradle-plugin` is the central orchestrator that automates the setup of KSP and
dependencies across Multiplatform projects. This document details its internal mechanisms.

---

## 1. Core Responsibilities

The plugin manages the following aspects of a project:

1. **Plugin Orchestration**: Automatically applies `com.google.devtools.ksp`.
2. **Automagic Dependency Injection**: 
    - Adds `kproxyable-runtime` to `commonMain` and `commonTest`.
    - Automatically adds `kproxyable-processor` to **all** relevant KSP configurations 
      (e.g., `kspJvm`, `kspJs`, `kspIosArm64`, etc.). Manual dependency blocks are no longer required.
3. **KSP Configuration**: Automatically configures KSP for every target (Jvm, Js, WasmJs, Native).
4. **Task Wiring**: Ensures correct execution order between KSP and compilation tasks.

---

## 2. Platform Strategy

KProxyable is strictly built for **Kotlin Multiplatform (KMP)**. The plugin requires the 
`org.jetbrains.kotlin.multiplatform` plugin to be applied to the project.

- **Why KMP?**: KProxyable's zero-reflection discovery mechanism relies on the `expect/actual` 
  feature of Kotlin. 
- **Single-Target Projects**: Projects targeting only JVM or JS are encouraged to use a 
  Single-Target KMP configuration (e.g., applying the multiplatform plugin but only defining 
  the `jvm()` target).

---

## 3. Version Baseline Strategy

To ensure KProxyable is compatible with the entire Kotlin 2.x lineage (from 2.0.0 to 2.4.x), the
plugin uses a **Baseline Strategy**:

- **Compilation Baseline**: The plugin itself is compiled against a stable baseline (Kotlin 2.0.21).
  This prevents "Metadata Version Mismatch" errors when applied to projects using much newer or
  experimental Kotlin versions.
- **Runtime Flexibility**: Since the plugin communicates with KSP via generic providers and
  command-line arguments, it remains compatible with newer KSP2 versioning schemes automatically.

---

## 4. Truly Unified Setup

The current plugin implements a **Truly Unified** model:

- Every module defines its own local registry.
- Every module with an `expect object KProxy` receives an `actual` implementation that
  statically links all discovered dependencies.

### Full Classpath Injection

The processor needs to know the full classpath to find "breadcrumb" files in dependencies.

- **The Solution**: Uses a `project.provider` to lazily resolve the paths only during task
  execution. This avoids "Configuration already resolved" errors during the Gradle configuration
  phase.
- **Argument**: Passed via `kproxyable.fullClasspath`.

---

## 5. Web Resource Handling (JS/WasmJs)

In web targets, KSP-generated resources (like `META-INF/services`) are often missed by default.

- **The Fix**:
    1. Locates the target-specific KSP resource output folder.
    2. Adds it as a source directory to the compilation's resources.
    3. Configures `ProcessResources` and `compileKotlin` tasks to explicitly depend on the
       corresponding KSP task to ensure resources are generated before bundling.

---

## 6. KSP Compiler Arguments

| Argument                | Value Type | Description                                                       |
|:------------------------|:-----------|:------------------------------------------------------------------|
| `kproxyable.moduleName` | String     | A sanitized, unique identifier for the module's registry.         |
| `kproxyable.isTest`     | Boolean    | Flags if we are generating for a Test source set.                 |
| `kproxyable.fullClasspath` | String  | Path-separated list of all dependencies for breadcrumb discovery. |
