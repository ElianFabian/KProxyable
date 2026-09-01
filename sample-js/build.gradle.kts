plugins {
    kotlin("multiplatform")
    alias(libs.plugins.ksp)
    id("io.github.elianfabian.kproxyable")
}

kotlin {
    js {
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
