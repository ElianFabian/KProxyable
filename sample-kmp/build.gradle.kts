plugins {
	alias(libs.plugins.kotlin.multiplatform)
	id("io.github.elianfabian.kproxyable")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

	jvm {
		withJava()
		mainRun {
			mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
		}
	}

	js(IR) {
		binaries.executable()
		browser()
		nodejs()
	}
	wasmJs {
		binaries.executable()
		browser()
		nodejs()
	}

	val nativeTargets = listOf(
		iosX64(), iosArm64(), iosSimulatorArm64(),
		macosX64(), macosArm64(),
		tvosX64(), tvosArm64(), tvosSimulatorArm64(),
		watchosX64(), watchosArm64(), watchosSimulatorArm64(), watchosDeviceArm64(),
		linuxX64(), linuxArm64(), mingwX64()
	)

	nativeTargets.forEach {
		it.binaries.executable {
			entryPoint = "com.elianfabian.kproxyable.sample.main"
		}
	}

	sourceSets {
		commonMain.dependencies {
			implementation(project(":sample-common"))
			implementation(libs.kotlinx.coroutines.core)
		}
	}
}

// Only for development, to force KSP to run every time, even if nothing changed
tasks.matching { it.name.startsWith("kspKotlin") }.configureEach {
	outputs.upToDateWhen { false }
}
