plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	wasmJs {
		nodejs()
		binaries.executable()
	}

	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(project(":sample-common"))
                implementation(project(":kproxyable-runtime"))
			}
		}
	}
}

dependencies {
    add("kspWasmJs", project(":kproxyable-processor"))
}

ksp {
    arg("kproxyable.moduleName", "sample_wasmjs")
    arg("kproxyable.dependencyModules", "sample_common")
}
