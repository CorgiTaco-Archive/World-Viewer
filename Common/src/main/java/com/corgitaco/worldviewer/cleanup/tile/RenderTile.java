package com.corgitaco.worldviewer.cleanup.tile;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.stream.Collectors;

public class RenderTile {


    private final HashMap<String, TileLayer> tileLayers = new LinkedHashMap<>();
    private DataTileManager tileManager;
    private final int tileWorldX;
    private final int tileWorldZ;
    private final int size;
    private final int sampleRes;

    public RenderTile(DataTileManager tileManager, Map<String, TileLayer.Factory> factories, int scrollY, int tileWorldX, int tileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2) {
        this.tileManager = tileManager;
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
        this.size = size;
        this.sampleRes = sampleRes;
        LongSet sampledChunks = new LongOpenHashSet();
        factories.forEach((s, factory) -> tileLayers.put(s, factory.make(tileManager, scrollY, tileWorldX, tileWorldZ, size, sampleRes, worldScreenv2, sampledChunks)));
        sampledChunks.forEach(tileManager::unloadTile);
    }


    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY)).filter(Objects::nonNull).map(mutableComponent -> (Component) mutableComponent).collect(Collectors.toList());
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender, Map<String, Float> opacity) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO, GlStateManager.SourceFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.DestFactor.SRC_ALPHA);
        tileLayers.forEach((key, value) -> {
            float layerOpacity = opacity.getOrDefault(key, 1F);
            if (layerOpacity > 0F) {
                DynamicTexture image = value.getImage();
                if (image != null) {
                    renderImage(stack, image, layerOpacity, value.brightness());
                }
            }
        });
        RenderSystem.disableBlend();

    }

    private void renderImage(PoseStack stack, DynamicTexture texture, float opacity, float brightness) {
        if (texture.getPixels() == null) {
            return;
        }
        RenderSystem.setShaderColor(brightness * opacity, brightness * opacity, brightness * opacity, opacity);
        RenderSystem.setShaderTexture(0, texture.getId());
        GuiComponent.blit(stack, 0, 0, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public void afterTilesRender(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender, Map<String, Float> opacity) {
        tileLayers.forEach((key, value) -> {
            float layerOpacity = opacity.getOrDefault(key, 1F);
            if (layerOpacity > 0F) {
                value.afterTilesRender(stack, screenTileMinX, screenTileMinZ, 0, 0, layerOpacity);
            }
        });
    }

    public void close() {
        for (TileLayer value : this.tileLayers.values()) {
            value.close();
        }
//        forEachChunkPos(pos -> this.tileManager.unloadTile(pos));
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
