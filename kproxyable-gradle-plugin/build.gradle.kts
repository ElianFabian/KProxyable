plugins {
	`kotlin-dsl`
	id("java-gradle-plugin")
}

repositories {
	mavenCentral()
	google()
}

dependencies {
	// Gradle Plugin de Kotlin directo, sin libs
	implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
}

gradlePlugin {
	plugins {
		create("kproxyablePlugin") {
			id = "com.elianfabian.kproxyable"
			implementationClass = "com.elianfabian.kproxyable.KProxyableGradlePlugin"
			displayName = "KProxyable Gradle Plugin"
			description = "Compiler plugin and KSP setup for KProxyable"
		}
	}
}
