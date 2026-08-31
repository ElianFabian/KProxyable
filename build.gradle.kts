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

    // Authoritatively link signing to the publishing plugin
    plugins.withId("com.vanniktech.maven.publish") {
        val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
        if (!isSnapshot) {
            // Using the string name of the extension to avoid "The value for this property is final" 
            // errors caused by re-configuring SonatypeHost/Coordinates.
            val mavenPublishing = project.extensions.getByName("mavenPublishing")
            try {
                mavenPublishing::class.java.getMethod("signAllPublications").invoke(mavenPublishing)
            } catch (e: Exception) {}
        }
    }
}
