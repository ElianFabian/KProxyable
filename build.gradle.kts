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

            // Determine if we have signing credentials
            // Vanniktech plugin looks for standard 'signing.keyId' or 'signingInMemoryKeyId'
            val hasKeys = project.findProperty("signing.keyId") != null || 
                         project.findProperty("signingInMemoryKeyId") != null

            // On CI, we want to sign all releases. 
            // We only skip if it's a snapshot or if we're not on CI and don't have keys.
            if (!isSnapshot && (isCI || hasKeys)) {
                signAllPublications()
            }
        }
    }
}
