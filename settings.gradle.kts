pluginManagement {
	repositories {
		maven("https://repo.spongepowered.org/repository/maven-public/")
		maven("https://maven.fabricmc.net/")
		maven("https://maven.architectury.dev/")
		maven("https://files.minecraftforge.net/maven/")
		mavenCentral()
		gradlePluginPortal()
	}
}
rootProject.name = "World-Viewer"
include("common", "fabric", "forge")