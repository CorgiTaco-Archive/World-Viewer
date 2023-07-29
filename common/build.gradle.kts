plugins {
    id("org.spongepowered.gradle.vanilla") version "0.2.1-SNAPSHOT"
}

group = "com.chaottic"
version = "1.0-SNAPSHOT"

minecraft {
    version("1.19.2")
}

dependencies {
    implementation("org.joml:joml:1.10.5")
    testImplementation("org.joml:joml:1.10.5")
}