plugins {
    kotlin("multiplatform")
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
