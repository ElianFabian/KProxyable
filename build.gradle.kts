plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.ksp) apply false
	alias(libs.plugins.maven.publish) apply false
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        yarnLockMismatchReport = org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.NONE
        reportNewYarnLock = false
        yarnLockAutoReplace = true
    }
}

allprojects {
	repositories {
		mavenCentral()
		google()
	}

	// GLOBAL SIGNING CONFIGURATION
	plugins.withType<SigningPlugin> {
		configure<SigningExtension> {
			val keyId = (project.findProperty("signing.keyId") ?: project.findProperty("signingInMemoryKeyId")) as String?
			val password = (project.findProperty("signing.password") ?: project.findProperty("signingInMemoryKeyPassword")) as String?
			val secretKey = (project.findProperty("signing.secretKey") ?: project.findProperty("signingInMemoryKey")) as String?

			if (keyId != null && password != null && secretKey != null) {
				useInMemoryPgpKeys(keyId, secretKey, password)
			}
		}
	}

	plugins.withId("com.vanniktech.maven.publish") {
		val isSnapshot = project.version.toString().endsWith("SNAPSHOT")
		if (!isSnapshot) {
			val mavenPublishing = project.extensions.getByName("mavenPublishing")
			try {
				mavenPublishing::class.java.getMethod("signAllPublications").invoke(mavenPublishing)
			}
			catch (e: Exception) {
			}
		}
	}
}

// ARROGANTLY DISABLE SIGNING IN MATRIX MODE
allprojects {
    afterEvaluate {
        if (rootProject.findProperty("kproxyable.matrix") == "true") {
            tasks.matching { it.name.contains("sign", ignoreCase = true) }.configureEach {
                enabled = false
            }
            project.extensions.extraProperties["vanniktech.publish.signing.required"] = "false"
        }
    }
}
