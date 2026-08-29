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

    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // Using CENTRAL_PORTAL for modern Sonatype accounts (central.sonatype.com)
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            
            val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
            
            // Auto-resolve relative GPG file path (only for local development)
            if (project.hasProperty("signing.secretKeyRingFile")) {
                val gpgFile = project.property("signing.secretKeyRingFile") as String
                if (!gpgFile.startsWith("/") && !gpgFile.contains(":\\")) {
                    val resolved = rootProject.file(gpgFile)
                    if (resolved.exists()) {
                        project.extensions.extraProperties["signing.secretKeyRingFile"] = resolved.absolutePath
                    }
                }
            }

            // Only attempt signing if we have a configured signatory
            val hasSigningConfig = project.hasProperty("signing.keyId") && 
                                   (project.hasProperty("signing.secretKey") || project.hasProperty("signing.secretKeyRingFile"))

            if (!isSnapshot && hasSigningConfig) {
                signAllPublications()
            }
        }
    }
}
