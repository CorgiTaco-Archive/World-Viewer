package com.corgitaco.worldviewer.cleanup.tile;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

public class RenderTile {


    private final HashMap<String, TileLayer> tileLayers = new HashMap<>();
    private DataTileManager tileManager;
    private final int tileWorldX;
    private final int tileWorldZ;
    private final int size;

    public RenderTile(DataTileManager tileManager, Map<String, TileLayer.Factory> factories, int scrollY, int tileWorldX, int tileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2) {
        this.tileManager = tileManager;
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
        this.size = size;
        factories.forEach((s, factory) -> tileLayers.put(s, factory.make(tileManager, scrollY, tileWorldX, tileWorldZ, size, sampleRes, worldScreenv2)));
        forEachChunkPos(tileManager::unloadTile);
    }


    public List<Component> toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseScreenX, mouseScreenY, mouseWorldX, mouseWorldZ, mouseTileLocalX, mouseTileLocalY)).filter(Objects::nonNull).map(mutableComponent -> (Component) mutableComponent).collect(Collectors.toList());
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender, Map<String, Float> opacity) {
        tileLayers.forEach((key, value) -> {
            float layerOpacity = opacity.getOrDefault(key, 1F);
            if (layerOpacity > 0F) {
                DynamicTexture image = value.getImage();
                if (image != null) {
                    renderImage(stack, screenTileMinX, screenTileMinZ, image, layerOpacity);
                }
            }
        });
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


    public void forEachChunkPos(LongConsumer pos) {
        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) - x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) - z;
                pos.accept(ChunkPos.asLong(chunkX, chunkZ));
            }
        }
    }

    private void renderImage(PoseStack stack, int screenTileMinX, int screenTileMinZ, DynamicTexture texture, float opacity) {
        if (texture.getPixels() == null) {
            return;
        }
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, 0, 0, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public Map<String, TileLayer> getTileLayers() {
        return tileLayers;
    }
}
