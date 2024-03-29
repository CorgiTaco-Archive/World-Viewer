package com.corgitaco.worldviewer.cleanup.tile;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class RenderTile {


    private final HashMap<String, TileLayer> tileLayers = new LinkedHashMap<>();
    private DataTileManager tileManager;
    private final int tileWorldX;
    private final int tileWorldZ;
    private final int size;
    private final int sampleRes;
    private WorldScreenv2 worldScreenv2;

    private final LongSet sampledChunks = new LongOpenHashSet();

    public RenderTile(DataTileManager tileManager, Map<String, TileLayer.Factory> factories, int scrollY, int tileWorldX, int tileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2, @Nullable RenderTile lastResolution) {
        this.tileManager = tileManager;
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
        this.size = size;
        this.sampleRes = sampleRes;
        this.worldScreenv2 = worldScreenv2;

        if (lastResolution != null) {
            sampledChunks.addAll(lastResolution.sampledChunks);

            lastResolution.tileLayers.forEach((s, layer) -> {
                if (!layer.usesLod()) {
                    tileLayers.put(s, layer);
                }
            });
        }
        factories.forEach((s, factory) -> tileLayers.computeIfAbsent(s, (s1) -> factory.make(tileManager, scrollY, tileWorldX, tileWorldZ, size, sampleRes, worldScreenv2, sampledChunks)));
        if (sampleRes == worldScreenv2.sampleResolution) {
            sampledChunks.forEach(tileManager::unloadTile);
        }
    }


    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY)).filter(Objects::nonNull).map(mutableComponent -> (Component) mutableComponent).collect(Collectors.toList());
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender, Map<String, Float> opacity) {
//        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR,  GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        tileLayers.forEach((key, value) -> {
            float layerOpacity = opacity.getOrDefault(key, 1F);
            if (layerOpacity > 0F) {
                value.render(stack, layerOpacity, tileLayers);
            }
        });
        RenderSystem.disableBlend();
    }



    public void afterTilesRender(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender, Map<String, Float> opacity) {
        tileLayers.forEach((key, value) -> {
            float layerOpacity = opacity.getOrDefault(key, 1F);
            if (layerOpacity > 0F) {
                value.afterTilesRender(stack, screenTileMinX, screenTileMinZ, 0, 0, layerOpacity);
            }
        });
    }

    public void close(boolean closeAll) {
        for (TileLayer value : this.tileLayers.values()) {
            if (!closeAll) {
                if (value.usesLod()) {
                    value.close();
                }
            } else {
                value.close();
            }
        }
    }

    public int getTileWorldX() {
        return tileWorldX;
    }

    public int getTileWorldZ() {
        return tileWorldZ;
    }



    public Map<String, TileLayer> getTileLayers() {
        return tileLayers;
    }

    public int getSampleRes() {
        return sampleRes;
    }

    public int getSize() {
        return size;
    }
}
