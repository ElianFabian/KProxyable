plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.ksp)
	id("io.github.elianfabian.kproxyable")
	application
}

kotlin {
	jvm()
	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(project(":sample-common"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
	}
}

application {
	mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
}

// Ensure the application 'run' task includes all KMP dependencies and KSP outputs
tasks.withType<JavaExec>().configureEach {
	val jvm = kotlin.targets.getByName("jvm") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
	classpath = jvm.compilations.getByName("main").output.allOutputs +
		project.configurations.getByName("jvmRuntimeClasspath")
}
