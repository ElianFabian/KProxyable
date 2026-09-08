# Gradle Properties Setup Guide

This document explains how to configure the properties for the KProxyable project.

## 🏗️ Architecture Overview

The project splits configuration into two files:

1. **`gradle.metadata.properties` (Public)**: Project identity and version matrix. **Tracked by Git**.
2. **`gradle.properties` (Private)**: Sensitive credentials. **Ignored by Git**.

---

## 🌍 Public Metadata (`gradle.metadata.properties`)

This file defines the project's identity and its compatibility matrix.

### Versioning

| Key                      | Description                                                  |
|:-------------------------|:-------------------------------------------------------------|
| `version`                | The current stable version of KProxyable (e.g., `1.1.1`).    |
| `kotlin`                 | The target Kotlin version used for library and samples.      |
| `ksp`                    | The target KSP version used for library and samples.         |
| `plugin.baseline.kotlin` | The stable version used to compile the Gradle Plugin itself. |
| `plugin.baseline.ksp`    | The stable version used to compile the Gradle Plugin itself. |

> [!NOTE]
> The `plugin.baseline` versions ensure that the KProxyable Gradle Plugin can be built in older 
> Gradle environments while still supporting newer Kotlin versions (like 2.4.x) in the projects 
> it is applied to.

### POM Metadata

Standard keys like `group`, `POM_NAME`, `POM_URL`, and `POM_SCM_*` are used to generate the 
Maven publications and must be kept accurate for Maven Central compliance.

---

## 🔐 Private Secrets (`gradle.properties`)

This file contains the credentials required to push artifacts. **Keep this file secure.**

- `mavenCentralUsername` / `Password`: Deployment tokens for Sonatype.
- `gradle.publish.key` / `secret`: API keys for the Gradle Plugin Portal.
- `signing.keyId` / `password`: GPG signing credentials.

---

## 🚀 Version Matrix Testing

KProxyable is verified across the Kotlin 2.x lineage. You can use the scripts in `scratch/` to 
verify specific versions:

```powershell
# Test a specific version
powershell -File scratch/run_matrix.ps1 -Version 2.2.0

# Test all verified versions (2.0 - 2.4)
powershell -File scratch/test_all_versions.ps1
```
