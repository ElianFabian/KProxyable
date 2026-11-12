plugins {
	id("org.jetbrains.kotlin.jvm")
	id("com.google.devtools.ksp")
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

	testImplementation(kotlin("test-junit5"))
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.test {
	useJUnitPlatform()
}

// Test-only KSP observer, never packaged in the published processor jar.
val kspProbe by sourceSets.creating
dependencies {
	add(kspProbe.implementationConfigurationName, libs.ksp.api)
}
val kspProbeJar by tasks.registering(Jar::class) {
	archiveClassifier.set("ksp-probe")
	from(kspProbe.output)
}
val kspProbeElements = configurations.create("kspProbeElements") {
	isCanBeConsumed = true
	isCanBeResolved = false
}
artifacts.add(kspProbeElements.name, kspProbeJar)
