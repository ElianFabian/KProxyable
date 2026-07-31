plugins {
    alias(libs.plugins.kotlin.jvm)
    id("com.elianfabian.kproxyable")
    application
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

application {
    mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
}
