package com.corgitaco.worldviewer.cleanup.tile;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.example.examplemod.Constants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.*;

public class TileV2 {


    private final HashMap<String, TileLayer> tileLayers = new HashMap<>();
    private final int tileWorldX;
    private final int tileWorldZ;
    private final int size;

    public TileV2(Map<String, TileLayer.Factory> factories, int scrollY, int tileWorldX, int tileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2) {
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
        this.size = size;
        Map<String, Object> cache = new HashMap<>();
        long beforeMs = System.currentTimeMillis();

        StringBuilder factoryTimings = new StringBuilder();
        factories.forEach((s, factory) -> {
            long beforeFactoryMs = System.currentTimeMillis();

            tileLayers.put(s, factory.make( null, cache, scrollY, tileWorldX, tileWorldZ, size, sampleRes, worldScreenv2.level, worldScreenv2));

            long afterFactoryMs = System.currentTimeMillis();
            if (!factoryTimings.isEmpty()) {
                factoryTimings.append(", ");
            }
            factoryTimings.append(s).append(": ").append(afterFactoryMs - beforeFactoryMs).append("ms");

        });
        Constants.LOGGER.info("Created tile %s,%s in %sms (%s) for tile size of %s blocks & sample resolution of 1/%s blocks.".formatted(tileWorldX, tileWorldZ, (System.currentTimeMillis() - beforeMs), factoryTimings.toString(), size, sampleRes));
    }


    public List<Component> toolTip(double mouseX, double mouseY, int mouseWorldX, int mouseWorldZ) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseX, mouseY, mouseWorldX, mouseWorldZ)).filter(Objects::nonNull).toList();
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender) {
        for (TileLayer value : tileLayers.values()) {
            DynamicTexture image = value.getImage();
            if (image != null) {
                renderImage(stack, screenTileMinX, screenTileMinZ, image, 1F);
            }
        }
    }

    public void afterTilesRender(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender) {
        for (TileLayer value : tileLayers.values()) {
            if (value.canRender(this, this.tileLayers.keySet())) {
                DynamicTexture image = value.getImage();
                value.afterTilesRender(stack, screenTileMinX, screenTileMinZ, 0, 0);
            }
        }
    }

    public void close() {
        for (TileLayer value : this.tileLayers.values()) {
            value.close();
        }
    }

    public int getTileWorldX() {
        return tileWorldX;
    }

    public int getTileWorldZ() {
        return tileWorldZ;
    }

    private void renderImage(PoseStack stack, int screenTileMinX, int screenTileMinZ, DynamicTexture texture, float opacity) {
        if (texture.getPixels() == null) {
            return;
        }
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    public Map<String, TileLayer> getTileLayers() {
        return tileLayers;
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        this.tileLayers.forEach((key, tileLayer) -> {
            compoundTag.put(key, tileLayer.save());
        });

        return compoundTag;
    }
}
