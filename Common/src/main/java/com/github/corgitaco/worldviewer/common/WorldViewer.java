package com.github.corgitaco.worldviewer.common;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class WorldViewer {
    public static final String MOD_ID = "worldviewer";

    public static final Logger LOGGER = LoggerFactory.getLogger("World Viewer");

    private WorldViewer() {
    }

    public static ResourceLocation createResourceLocation(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
