plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	js(IR) {
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
