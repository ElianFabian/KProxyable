import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":kproxyable-runtime"))
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

// Package ProGuard rules for consumers
tasks.named<Jar>("jar") {
    from("kproxyable-jvm.pro") {
        into("META-INF/proguard")
        rename { "kproxyable-jvm.pro" }
    }
}
