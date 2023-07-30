plugins {
    id("org.spongepowered.gradle.vanilla") version "0.2.1-SNAPSHOT"
    `java-library`
    kotlin("jvm") version "1.9.0"
}

group = "com.chaottic"
version = "1.0-SNAPSHOT"

minecraft {
    version("1.19.2")
}

dependencies {
    api("org.joml:joml:1.10.5")
    testImplementation("org.joml:joml:1.10.5")
}