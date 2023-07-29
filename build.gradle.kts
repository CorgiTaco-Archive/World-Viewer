plugins {
    id("java")
    id("io.freefair.lombok") version "8.1.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.freefair.lombok")
}

allprojects {
    group = "com.chaottic"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation(platform("org.junit:junit-bom:5.9.1"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.test {
        useJUnitPlatform()
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}