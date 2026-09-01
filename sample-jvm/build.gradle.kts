plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    id("io.github.elianfabian.kproxyable")
    application
}

dependencies {
    implementation(project(":sample-common"))
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
}
