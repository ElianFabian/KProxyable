plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

group = "com.elianfabian.kproxyable"
version = "1.0-SNAPSHOT"

gradlePlugin {
    plugins {
        create("kproxyable") {
            id = "com.elianfabian.kproxyable"
            implementationClass = "com.elianfabian.kproxyable.KProxyablePlugin"
            displayName = "KProxyable Gradle Plugin"
            description = "Automates KSP and runtime setup for KProxyable"
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
}
