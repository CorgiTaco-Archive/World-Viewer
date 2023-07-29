package dev.corgitaco.worldviewer.common;

import net.minecraft.resources.ResourceLocation;

public final class WorldViewer {
    private static final String MOD_ID = "worldviewer";

    public static ResourceLocation resourceLocation(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
