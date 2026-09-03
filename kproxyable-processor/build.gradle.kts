plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.ksp)
	id("com.vanniktech.maven.publish")
}

// Global publishing settings (Sonatype host, signing) are applied in root build.gradle.kts.
// Metadata is automatically injected from the root gradle.properties.

kotlin {
	explicitApi()
	jvmToolchain(21)
}

dependencies {
	implementation(project(":kproxyable-runtime"))

	implementation(libs.ksp.api)

	implementation(libs.kotlinpoet)
	implementation(libs.kotlinpoet.ksp)

	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
