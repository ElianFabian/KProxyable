@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.vanniktech.maven.publish")
}

kotlin {
	explicitApi()
	jvmToolchain(21)

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
		val nonJvmMain by creating {
			dependsOn(commonMain.get())
		}
		
		jsMain.get().dependsOn(nonJvmMain)
		wasmJsMain.get().dependsOn(nonJvmMain)
		
        // Link all native targets to nonJvmMain dynamically
        targets.all {
            if (platformType == KotlinPlatformType.native) {
                compilations.getByName("main").defaultSourceSet.dependsOn(nonJvmMain)
            }
        }
	}
}

tasks.named<Jar>("jvmJar") {
	from("kproxyable.pro") {
		into("META-INF/proguard")
	}
}
