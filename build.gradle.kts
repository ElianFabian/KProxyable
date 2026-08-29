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

    // BRIDGE CI SECRETS TO GRADLE PROPERTIES
    // Maps shell-safe underscore names to the dotted names required by plugins.
    listOf(
        "signing_keyId" to "signing.keyId",
        "signing_password" to "signing.password",
        "signing_secretKey" to "signing.secretKey",
        "signing_secretKeyRingFile" to "signing.secretKeyRingFile"
    ).forEach { (ciKey, gradleKey) ->
        if (project.hasProperty(ciKey)) {
            project.extensions.extraProperties[gradleKey] = project.property(ciKey)
        }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            // Using CENTRAL_PORTAL for modern Sonatype accounts (central.sonatype.com)
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
            
            val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
            
            // Auto-resolve relative GPG file path (for local development)
            if (project.hasProperty("signing.secretKeyRingFile")) {
                val gpgFile = project.property("signing.secretKeyRingFile") as String
                if (!gpgFile.startsWith("/") && !gpgFile.contains(":\\")) {
                    val resolved = rootProject.file(gpgFile)
                    if (resolved.exists()) {
                        project.extensions.extraProperties["signing.secretKeyRingFile"] = resolved.absolutePath
                    }
                }
            }

            // Only attempt signing if we have a valid signatory configured
            val hasSigningConfig = project.hasProperty("signing.keyId") && 
                                   (project.hasProperty("signing.secretKey") || project.hasProperty("signing.secretKeyRingFile"))

            if (!isSnapshot && hasSigningConfig) {
                signAllPublications()
            }
        }
    }
}
