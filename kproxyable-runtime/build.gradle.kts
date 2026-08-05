@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
	kotlin("multiplatform")
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
}

// Package ProGuard rules for JVM consumers
tasks.named<Jar>("jvmJar") {
    from("kproxyable.pro") {
        into("META-INF/proguard")
    }
}
