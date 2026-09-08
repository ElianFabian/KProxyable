plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	jvm()
	js(IR) {
		nodejs()
	}
	@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
	wasmJs {
		nodejs()
	}

	iosX64()
	iosArm64()
	iosSimulatorArm64()
	macosX64()
	macosArm64()
	tvosX64()
	tvosArm64()
	tvosSimulatorArm64()
	watchosX64()
	watchosArm64()
	watchosSimulatorArm64()
	watchosDeviceArm64()
	linuxX64()
	linuxArm64()
	mingwX64()

	sourceSets {
		commonMain.dependencies {
			api(project(":kproxyable-runtime"))
			implementation(libs.kotlinx.coroutines.core)
		}
		commonTest.dependencies {
			implementation(kotlin("test"))
		}
	}
}
