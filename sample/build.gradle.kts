plugins {
	kotlin("multiplatform")
	id("com.google.devtools.ksp")
	id("com.elianfabian.kproxyable")
}

kotlin {
	jvm()

	js {
		browser()
		nodejs()
	}
	wasmJs {
		browser()
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
			implementation(project(":kproxyable-runtime"))
		}
	}
}

dependencies {
//	ksp(project(":proxy-processor"))
	add("kspJvm", project(":kproxyable-processor"))
}


// Only for development, to force KSP to run every time, even if nothing changed
tasks.matching { it.name == "kspKotlinJvm" }.configureEach {
	outputs.upToDateWhen { false }
}
//tasks.withType<KotlinCompile>().configureEach {
//	dependsOn(":kproxyable-compiler-plugin:publishToMavenLocal")
//}
