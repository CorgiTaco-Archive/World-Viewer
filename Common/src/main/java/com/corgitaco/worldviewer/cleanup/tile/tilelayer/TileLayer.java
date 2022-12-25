package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.RenderTile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class TileLayer {

    public static final Supplier<Map<String, Factory>> FACTORY_REGISTRY = () -> {
        Map<String, Factory> map = new LinkedHashMap<>();
//        map.put("heights", HeightsLayer::new);
        map.put("biomes", BiomeLayer::new);
        map.put("slime_chunks", SlimeChunkLayer::new);
        map.put("structures", StructuresLayer::new);
        return map;
    };

    protected final DataTileManager dataTileManager;
    private final int tileWorldX;
    private final int tileWorldZ;

    public TileLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        this.dataTileManager = dataTileManager;
        this.tileWorldX = tileWorldX;
        this.tileWorldZ = tileWorldZ;
    }

    @Nullable
    public MutableComponent toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
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

    public boolean canRender(RenderTile renderTile, Collection<String> currentlyRendering) {
        return true;
    }

    @FunctionalInterface
    public interface Factory {

        TileLayer make(DataTileManager tileManager, int scrollWorldY, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen);
    }

    @NotNull
    public static NativeImage makeNativeImageFromColorData(int[][] data) {
        NativeImage nativeImage = new NativeImage(data.length, data.length, true);
        for (int x = 0; x < data.length; x++) {
            int[] colorRow = data[x];
            for (int y = 0; y < colorRow.length; y++) {
                int color = colorRow[y];
                nativeImage.setPixelRGBA(x, y, color);
            }
        }
        return nativeImage;
    }
}


