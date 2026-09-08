plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
	wasmJs {
		nodejs()
		binaries.executable()
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(project(":sample-common"))
			}
		}
	}
}

dependencies {
}
