plugins {
    id("org.spongepowered.gradle.vanilla") version "0.2.1-SNAPSHOT"
    `java-library`
    kotlin("jvm") version "1.9.0"
}

minecraft {
    version("1.19.2")
}

repositories {
    maven("https://repo.spongepowered.org/repository/maven-releases/")
}

dependencies {
    api("org.joml:joml:1.10.5")
    compileOnly("org.spongepowered:mixin:0.8.5")

    testImplementation("org.joml:joml:1.10.5")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}