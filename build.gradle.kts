plugins {
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.ksp) apply false
}

group = property("group")!!
version = property("version")!!

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
