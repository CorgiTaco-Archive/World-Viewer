plugins {
    id("java")
    id("jvm-class-extensions") version "1.3"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jvm-class-extensions")

    this.takeUnless { project -> project == project(":common")  }?.apply {

        dependencies {
            implementation(project(":common"))
        }

        sourceSets["main"].resources {
            this.source(project(":common").sourceSets["main"].resources)
        }
    }

    repositories {
        maven("https://maven.msrandom.net/repository/root")
        mavenCentral()
    }

    dependencies {
        implementation("net.msrandom:class-extension-annotations:1.0")

        testImplementation(platform("org.junit:junit-bom:5.9.1"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }

    tasks.test {
        useJUnitPlatform()
    }
}

allprojects {
    group = "com.chaottic"
    version = "1.0-SNAPSHOT"

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }
}