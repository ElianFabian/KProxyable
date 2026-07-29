plugins {
	kotlin("jvm")
	`maven-publish`
}

group = "com.elianfabian.kproxyable"
version = "1.0.0-SNAPSHOT"

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
			groupId = "com.elianfabian.kproxyable"
			artifactId = "kproxyable-compiler-plugin"
			version = "1.0.0-SNAPSHOT"
		}
	}
}

repositories {
	mavenCentral()
	google()
}

dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

	testImplementation("org.jetbrains.kotlin:kotlin-test")
	testImplementation("org.jetbrains.kotlin:kotlin-reflect")
}
