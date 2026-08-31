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
            val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
            val isCI = System.getenv("GITHUB_ACTIONS") == "true"
            
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

            // Enable signing for releases
            if (!isSnapshot && (isCI || project.findProperty("signing.keyId") != null)) {
                signAllPublications()
            }
        }
    }
}
