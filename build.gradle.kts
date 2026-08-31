import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin

plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.ksp) apply false
	id("com.vanniktech.maven.publish") version "0.30.0" apply false
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}

    // GLOBAL SIGNING CONFIGURATION
    // Ensures that ALL publications (Libraries + Plugin Markers) can find the GPG keys.
    // Supports both local file-based signing and CI in-memory signing.
    plugins.withType<SigningPlugin> {
        configure<SigningExtension> {
            val keyId = (project.findProperty("signing.keyId") ?: project.findProperty("signingInMemoryKeyId")) as String?
            val password = (project.findProperty("signing.password") ?: project.findProperty("signingInMemoryKeyPassword")) as String?
            val secretKey = (project.findProperty("signing.secretKey") ?: project.findProperty("signingInMemoryKey")) as String?

            if (keyId != null && password != null && secretKey != null) {
                useInMemoryPgpKeys(keyId, secretKey, password)
            }
            
            // Auto-resolve relative GPG file path for local development
            if (project.hasProperty("signing.secretKeyRingFile")) {
                val gpgFile = project.property("signing.secretKeyRingFile") as String
                if (!gpgFile.startsWith("/") && !gpgFile.contains(":\\")) {
                    val resolved = rootProject.file(gpgFile)
                    if (resolved.exists()) {
                        project.extensions.extraProperties["signing.secretKeyRingFile"] = resolved.absolutePath
                    }
                }
            }
        }
    }
}

// Global publishing settings like 'SONATYPE_HOST' are managed via gradle.metadata.properties
// and are automatically picked up by the 'com.vanniktech.maven.publish' plugin.
// By avoiding manual 'mavenPublishing { ... }' blocks in the root, we prevent property finalization errors.
