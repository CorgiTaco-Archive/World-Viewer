pluginManagement {
	repositories {
		maven("https://repo.spongepowered.org/repository/maven-public/")
		maven("https://maven.fabricmc.net/")
		maven("https://maven.architectury.dev/")
		maven("https://files.minecraftforge.net/maven/")
		maven("https://maven.msrandom.net/repository/root")
		mavenCentral()
		gradlePluginPortal()
	}
}
rootProject.name = "World-Viewer"
include("common", "fabric", "forge")