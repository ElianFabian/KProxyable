plugins {
    kotlin("multiplatform")
    id("com.elianfabian.kproxyable")
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
