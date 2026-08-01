plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.elianfabian.kproxyable")
}

kotlin {
    jvm()
    js(IR) {
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
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
