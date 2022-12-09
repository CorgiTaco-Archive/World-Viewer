package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.tile.TileV2;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class TileLayer {

    public static final Map<String, Factory> FACTORY_REGISTRY = Util.make(new LinkedHashMap<>(), map -> {
        map.put("heights", HeightsLayer::new);
//        map.put("biomes", BiomeLayer::new);
//        map.put("slime_chunks", SlimeChunkLayer::new);
//        map.put("structures", StructuresLayer::new);
    });

    private final int tileWorldX;
    private final int tileWorldZ;

    public TileLayer(CompoundTag compoundTag, Map<String, Object> cache, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen) {
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
    }

    public CompoundTag save() {
        return new CompoundTag();
    }

    @Nullable
    public Component toolTip(double mouseX, double mouseY, double mouseWorldX, double mouseWorldZ) {
        return null;
    }

    public void afterTilesRender(PoseStack stack, double mouseX, double mouseY, double mouseWorldX, double mouseWorldZ) {
    }


    @Nullable
    public DynamicTexture getImage() {
        return null;
    }

    public void close() {
    }

    public int getTileWorldX() {
        return tileWorldX;
    }

    public int getTileWorldZ() {
        return tileWorldZ;
    }

    public boolean canRender(TileV2 tileV2, Collection<String> currentlyRendering) {
        return true;
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(@Nullable CompoundTag tag, Map<String, Object> cache, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen);
    }
}


