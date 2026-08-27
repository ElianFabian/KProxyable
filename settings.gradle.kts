pluginManagement {
    // Load Metadata (Public) - custom file name requires manual loading
    val metadataPropsFile = file("gradle.metadata.properties")
    if (metadataPropsFile.exists()) {
        val metadataProps = java.util.Properties()
        metadataPropsFile.inputStream().use { metadataProps.load(it) }
        metadataProps.forEach { key, value ->
            settings.extensions.extraProperties[key as String] = value
        }
    }

	includeBuild("kproxyable-gradle-plugin")
	repositories {
		mavenLocal()
		google()
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		mavenLocal()
		google()
		mavenCentral()
	}
}

rootProject.name = "KProxyable"

include(":kproxyable-runtime")
include(":kproxyable-processor")
include(":sample-common")
include(":sample-kmp")
include(":sample-jvm")
include(":sample-js")

// Authoritative injection of metadata into every project
gradle.beforeProject {
    val metadataPropsFile = rootProject.file("gradle.metadata.properties")
    if (metadataPropsFile.exists()) {
        val metadataProps = java.util.Properties()
        metadataPropsFile.inputStream().use { metadataProps.load(it) }
        metadataProps.forEach { key, value ->
            val keyStr = key as String
            val valStr = value as String
            
            if (keyStr == "group") project.group = valStr
            if (keyStr == "version") project.version = valStr
            
            project.extensions.extraProperties[keyStr] = valStr
        }
    }
}
