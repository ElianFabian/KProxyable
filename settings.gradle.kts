pluginManagement {
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
include(":kproxyable-jvm")
include(":kproxyable-processor")
include(":sample-common")
include(":sample-kmp")
include(":sample-jvm")
