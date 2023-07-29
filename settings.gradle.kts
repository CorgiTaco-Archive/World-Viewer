pluginManagement {
	repositories {
		maven("https://repo.spongepowered.org/repository/maven-public/")
		mavenCentral()
		gradlePluginPortal()
	}
}
rootProject.name = "World-Viewer"
include("common", "fabric", "forge")