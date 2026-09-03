rootProject.name = "kproxyable-gradle-plugin"

// 1. Load Metadata (Public)
val metadataPropsFile = file("../gradle.metadata.properties")
if (metadataPropsFile.exists()) {
	val metadataProps = java.util.Properties()
	metadataPropsFile.inputStream().use { metadataProps.load(it) }
	metadataProps.forEach { key, value ->
		settings.extensions.extraProperties[key as String] = value
	}
}

// 2. Load Secrets (Private)
val secretPropsFile = file("../gradle.properties")
if (secretPropsFile.exists()) {
	val secretProps = java.util.Properties()
	secretPropsFile.inputStream().use { secretProps.load(it) }
	secretProps.forEach { key, value ->
		val k = key as String
		val v = value as String
		if (k.startsWith("gradle.publish.")) {
			System.setProperty(k, v)
		}
	}
}

dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			from(files("../gradle/libs.versions.toml"))
		}
	}
}

// Authoritative injection of metadata into the plugin project
gradle.beforeProject {
	val metadataFile = rootProject.file("../gradle.metadata.properties")
	if (metadataFile.exists()) {
		val metadataProps = java.util.Properties()
		metadataFile.inputStream().use { metadataProps.load(it) }
		metadataProps.forEach { key, value ->
			val keyStr = key as String
			val valStr = value as String

			if (keyStr == "group") project.group = valStr
			if (keyStr == "version") project.version = valStr

			project.extensions.extraProperties[keyStr] = valStr
		}
	}
}
