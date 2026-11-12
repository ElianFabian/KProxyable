plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
    id("io.github.elianfabian.kproxyable")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm()
}

// This project deliberately has no :kproxyable-runtime or :kproxyable-processor projects.
// Inspect declarations without resolving the not-yet-published release artifacts.
tasks.register("verifyKProxyableVersions") {
    doLast {
        val expected = providers.gradleProperty("expectedVersion").get()
        val runtime = configurations.getByName("commonMainImplementation").dependencies
            .single { it.name == "kproxyable-runtime" }
        val processor = configurations.getByName("kspJvm").dependencies
            .single { it.name == "kproxyable-processor" }
        val testProcessor = configurations.getByName("kspJvmTest").dependencies
            .single { it.name == "kproxyable-processor" }
        for (dependency in listOf(runtime, processor, testProcessor)) {
            check(dependency.group == "io.github.elianfabian" && dependency.version == expected) {
                "Unexpected dependency: ${dependency.group}:${dependency.name}:${dependency.version}"
            }
            println("Verified ${dependency.group}:${dependency.name}:${dependency.version}")
        }
    }
}
