pluginManagement {
	includeBuild("kproxyable-gradle-plugin")
	repositories {
		mavenLocal()
		google()
		mavenCentral()
		gradlePluginPortal()
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

include(":kproxyable-processor")
include(":kproxyable-runtime")
include(":sample-common")
include(":sample-jvm")
include(":sample-js")
include(":sample-wasmjs")
include(":sample-kmp")
