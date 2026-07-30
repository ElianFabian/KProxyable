plugins {
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.ksp)
}

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
