import net.fabricmc.loom.api.mappings.layered.spec.LayeredMappingSpecBuilder

plugins {
    id("fabric-loom") version "1.2-SNAPSHOT"
}

loom {
    runs {
        getByName("client") {
            client()
        }
        getByName("server") {
            server()
        }

        all {
            ideConfigGenerated(true)
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:1.19.2")
    mappings(loom.layered(LayeredMappingSpecBuilder::officialMojangMappings))

    modImplementation("net.fabricmc:fabric-loader:0.14.21")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.76.0+1.19.2")
}