plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.elianfabian.kproxyable")
}

kotlin {
    wasmJs {
        nodejs()
        binaries.executable()
    }
    
    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":sample-common"))
            }
        }
    }
}
