plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

group = property("group")!!
version = property("version")!!

val groupProp = group
val versionProp = version

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
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
}

// Generate a BuildConstants file to avoid hardcoding version and group in the plugin code
val generateBuildConstants by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/buildConstants/kotlin")
    inputs.property("group", groupProp)
    inputs.property("version", versionProp)
    outputs.dir(outputDir)

    doLast {
        val outputFile = outputDir.get().file("com/elianfabian/kproxyable/BuildConstants.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("""
            package com.elianfabian.kproxyable

            internal object BuildConstants {
                const val GROUP = "$groupProp"
                const val VERSION = "$versionProp"
            }
        """.trimIndent())
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildConstants)
    }
}
