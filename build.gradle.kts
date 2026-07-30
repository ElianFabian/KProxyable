plugins {
	kotlin("jvm") version "2.0.21" apply false
	kotlin("multiplatform") version "2.0.21" apply false
	id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

group = "com.elianfabian.kproxyable"
version = "1.0-SNAPSHOT"

allprojects {
	repositories {
		mavenCentral()
		google()
	}
}
