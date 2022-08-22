package com.corgitaco.worldviewer.client;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class WorldScreenStructureSpritesReloadListener extends SimplePreparableReloadListener<Object> {

    @Override
    protected Object prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Object o, ResourceManager manager, ProfilerFiller profiler) {
    }
}
