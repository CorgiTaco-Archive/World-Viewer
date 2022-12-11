package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.TileV2;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class HeightsLayer extends TileLayer {

    private final DynamicTexture heights;


    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, screen);

        NativeImage image = new NativeImage(size, size, true);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, 0, worldZ - sampleZ);

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;
                        image.setPixelRGBA(dataX, dataZ, grayScale);
                    }
                }
            }
        }
        this.heights = new DynamicTexture(image);
    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, heightAccessor.getMinBuildHeight(), heightAccessor.getHeight()), 0, 1F);
        int color = Math.round(Mth.clampedLerp(128, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public boolean canRender(TileV2 tileV2, Collection<String> currentlyRendering) {
        return !currentlyRendering.contains("biomes");
    }

    @Override
    public @Nullable DynamicTexture getImage() {
        return this.heights;
    }
}
