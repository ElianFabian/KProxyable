pluginManagement {
	repositories {
		google()
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "KProxyable"

include(":kproxyable-runtime")
include(":kproxyable-processor")
include(":sample")
