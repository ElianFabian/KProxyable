plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.maven.publish) apply false
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
            val hasSigningKey = project.hasProperty("signing.keyId") || System.getenv("GPG_SIGNING_KEY") != null
            
            // Auto-resolve relative GPG file path
            if (project.hasProperty("signing.secretKeyRingFile")) {
                val gpgFile = project.property("signing.secretKeyRingFile") as String
                if (!gpgFile.startsWith("/") && !gpgFile.contains(":\\")) {
                    val resolved = rootProject.file(gpgFile)
                    if (resolved.exists()) {
                        project.extensions.extraProperties["signing.secretKeyRingFile"] = resolved.absolutePath
                    }
                }
            }

            if (!isSnapshot && hasSigningKey) {
                signAllPublications()
            }
        }
    }
}
