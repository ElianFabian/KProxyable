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
    // This ensures that ALL publications (Libraries + Plugin Markers) can find the GPG keys
    plugins.withType<SigningPlugin> {
        configure<SigningExtension> {
            val keyId = project.findProperty("signing.keyId") as String?
            val password = project.findProperty("signing.password") as String?
            val secretKey = project.findProperty("signing.secretKey") as String?

            if (keyId != null && password != null && secretKey != null) {
                useInMemoryPgpKeys(keyId, secretKey, password)
            }
        }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // CENTRAL_PORTAL is required for all new Sonatype accounts
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            
            val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
            if (!isSnapshot) {
                // Vanniktech handles its own signing linkage
                signAllPublications()
            }
        }
    }
}
