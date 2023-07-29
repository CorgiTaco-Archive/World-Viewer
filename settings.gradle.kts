pluginManagement {
	repositories {
		maven("https://repo.spongepowered.org/repository/maven-public/")
		maven("https://maven.fabricmc.net/")
		mavenCentral()
		gradlePluginPortal()
	}
}
rootProject.name = "World-Viewer"
include("common", "fabric", "forge")