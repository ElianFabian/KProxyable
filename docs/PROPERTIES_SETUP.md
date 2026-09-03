# Gradle Properties Setup Guide

This document explains how to configure the Gradle properties for the KProxyable project. To ensure
security and maintainability, the project uses a dual-property file system.

## 🏗️ Architecture Overview

The project splits configuration into two files:

1. **`gradle.metadata.properties` (Public)**: Contains project identity, licensing, and developer
   info. This file is **tracked by Git**.
2. **`gradle.properties` (Private)**: Contains sensitive credentials, API keys, and GPG signing
   details. This file is **ignored by Git** and should never be committed.

---

## 🌍 Public Metadata (`gradle.metadata.properties`)

This file defines the public face of the library. It is required for Maven Central compliance.

| Key               | Description                               | Example                                     |
|:------------------|:------------------------------------------|:--------------------------------------------|
| `group`           | The Maven group ID / namespace.           | `io.github.elianfabian`                     |
| `version`         | The current stable version.               | `1.0.0`                                     |
| `POM_NAME`        | Human-readable name of the project.       | `KProxyable`                                |
| `POM_DESCRIPTION` | A brief summary of what the library does. | `Compile-time Dynamic Proxies...`           |
| `POM_URL`         | The home page of the project.             | `https://github.com/ElianFabian/KProxyable` |
| `POM_LICENCE_*`   | License type and official URL.            | `MIT License`                               |
| `POM_SCM_*`       | Connection strings for version control.   | `scm:git:github.com/...`                    |
| `POM_DEVELOPER_*` | ID, Name, and Email of the maintainer.    | `elianfabian`, `Elián Fabián`               |

---

## 🔐 Private Secrets (`gradle.properties`)

This file contains the credentials required to push artifacts to external portals. **Keep this file
secure.**

### 📦 Maven Central (Sonatype)

Required for the `publishAndReleaseToMavenCentral` task.

- `mavenCentralUsername`: Your Sonatype Central Deployment Token username.
- `mavenCentralPassword`: Your Sonatype Central Deployment Token password.

### 🛠️ Gradle Plugin Portal

Required for the `:kproxyable-gradle-plugin:publishPlugins` task.

- `gradle.publish.key`: Your API Key from [plugins.gradle.org](https://plugins.gradle.org/).
- `gradle.publish.secret`: Your API Secret.

### ✍️ GPG Signing

Required for all Maven Central publications.

- `signing.keyId`: The last 8 characters of your public GPG key ID.
- `signing.password`: The passphrase for your GPG key.
- `signing.secretKeyRingFile`: The relative path to your exported secret key ring (e.g.,
  `kproxyable.gpg`).

---

## 🔑 GPG Key Setup

To publish to Maven Central, your artifacts must be signed, and your public key must be available on
a keyserver.

1. **Generate a Key**:
   ```bash
   gpg --full-gen-key
   ```
2. **Find your Key ID**:
   List your keys to find the one you just created:
   ```bash
   gpg --list-keys
   ```
3. **Upload to Keyserver**:
   Maven Central requires your public key to be discoverable. Replace `$publicKey` with your ID:
   ```bash
   gpg --keyserver keyserver.ubuntu.com --send-keys $publicKey
   ```
4. **Export and Dearmor the Secret Key**:
   Export your secret key and convert it to the binary format required by Gradle:
   ```powershell
   # Export the armored key to a variable
   $privateKey = gpg --armor --export-secret-keys $publicKey

   # Dearmor it and write to the file (ensure this file is in .gitignore)
   $privateKey | gpg --dearmor > kproxyable.gpg
   ```

---

## ⚙️ Technical Note: Included Build Isolation

The `kproxyable-gradle-plugin` is an **included build**. In Gradle, these are isolated processes.

To ensure the plugin can see these properties during its own publishing cycle, we have added manual
loading logic in its `settings.gradle.kts`. This ensures that even though it is a separate build, it
remains synchronized with your root project's credentials and versioning.

> [!TIP]
> If you add a new secret property that needs to be shared with the plugin, ensure it is handled in
> the loading block of `kproxyable-gradle-plugin/settings.gradle.kts`.
