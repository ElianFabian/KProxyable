import org.gradle.api.publish.maven.MavenPublication

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.0.0"
    `maven-publish`
    signing
}

// POM metadata is automatically injected from gradle.properties for Portal publishing.
// We manually bridge the same values for the local Maven publication below.

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

gradlePlugin {
    website.set(project.findProperty("POM_URL") as? String)
    vcsUrl.set(project.findProperty("POM_SCM_URL") as? String)
    
    plugins {
        register("kproxyable") {
            id = "io.github.elianfabian.kproxyable"
            implementationClass = "com.elianfabian.kproxyable.KProxyablePlugin"
            displayName = "KProxyable Gradle Plugin"
            description = "Compile-time dynamic proxy generation for Kotlin Multiplatform. Automates KSP setup for cross-platform interface interception."
            tags.set(listOf("kotlin", "multiplatform", "proxy", "ksp", "code-generation"))
        }
    }
}

// Ensure the local Maven publication has the same metadata
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.findProperty("POM_NAME") as? String)
            description.set(project.findProperty("POM_DESCRIPTION") as? String)
            url.set(project.findProperty("POM_URL") as? String)
            
            licenses {
                license {
                    name.set(project.findProperty("POM_LICENCE_NAME") as? String)
                    url.set(project.findProperty("POM_LICENCE_URL") as? String)
                }
            }
            developers {
                developer {
                    id.set(project.findProperty("POM_DEVELOPER_ID") as? String)
                    name.set(project.findProperty("POM_DEVELOPER_NAME") as? String)
                    email.set(project.findProperty("POM_DEVELOPER_EMAIL") as? String)
                }
            }
            scm {
                connection.set(project.findProperty("POM_SCM_CONNECTION") as? String)
                developerConnection.set(project.findProperty("POM_SCM_DEV_CONNECTION") as? String)
                url.set(project.findProperty("POM_SCM_URL") as? String)
            }
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
    inputs.property("group", project.group)
    inputs.property("version", project.version)
    outputs.dir(outputDir)

    doLast {
        val outputFile = outputDir.get().file("com/elianfabian/kproxyable/BuildConstants.kt").asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText("""
            package com.elianfabian.kproxyable

            internal object BuildConstants {
                const val GROUP = "${project.group}"
                const val VERSION = "${project.version}"
            }
        """.trimIndent())
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildConstants)
    }
}
