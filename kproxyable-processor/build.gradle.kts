plugins {
	kotlin("jvm")
	id("com.google.devtools.ksp")
}

kotlin {
	explicitApi()
	jvmToolchain(21)
}

dependencies {
	implementation(project(":kproxyable-runtime"))

	implementation("com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28")

	implementation("com.squareup:kotlinpoet:1.18.0")
	implementation("com.squareup:kotlinpoet-ksp:1.18.0")

	testImplementation(kotlin("test"))
}

tasks.test {
	useJUnitPlatform()
}
