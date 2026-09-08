plugins {
	id("org.jetbrains.kotlin.multiplatform")
	id("com.google.devtools.ksp")
	id("io.github.elianfabian.kproxyable")
}

kotlin {
	jvm {
		mainRun {
			mainClass.set("com.elianfabian.kproxyable.sample.MainKt")
		}
	}
	sourceSets {
		val commonMain by getting {
			dependencies {
				implementation(project(":sample-common"))
				implementation(libs.kotlinx.coroutines.core)
			}
		}
	}
}

dependencies {
	kotlin.targets.forEach { target ->
		if (target.name != "metadata") {
			val targetName = target.name.replaceFirstChar { it.uppercase() }
			add("ksp$targetName", project(":kproxyable-processor"))
			add("ksp${targetName}Test", project(":kproxyable-processor"))
		}
	}
}

ksp {
    arg("kproxyable.linkModules", "sample_common")
}

// In some Kotlin versions, project dependencies in jvmRun don't include generated resources automatically
tasks.withType<JavaExec>().configureEach {
    if (name == "jvmRun") {
        val commonProject = project(":sample-common")
        dependsOn(commonProject.tasks.matching { it.name.startsWith("kspKotlinJvm") })
        
        // Add generated resources directory of common project
        val commonGeneratedResources = commonProject.layout.buildDirectory.dir("generated/ksp/jvm/jvmMain/resources")
        classpath += commonProject.files(commonGeneratedResources)
    }
}
