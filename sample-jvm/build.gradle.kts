plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	jvm {
		mainRun {
			mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
		}
	}
	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(project(":sample-common"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
	}
}
