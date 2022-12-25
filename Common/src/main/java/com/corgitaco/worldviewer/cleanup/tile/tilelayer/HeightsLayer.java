package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.RenderTile;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class HeightsLayer extends TileLayer {

    @Nullable
    private DynamicTexture lazy;

    private int[][] colorData;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, screen);


        colorData = new int[size][size];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX + sampleX, 0, worldZ + sampleZ);

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;
                        colorData[dataX][dataZ] = grayScale;
                    }
                }
            }
        }
    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, 0, 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(127, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public boolean canRender(RenderTile renderTile, Collection<String> currentlyRendering) {
        return !currentlyRendering.contains("biomes");
    }

    @Override
    public @Nullable DynamicTexture getImage() {
        if (lazy == null) {
            this.lazy = new DynamicTexture(makeNativeImageFromColorData(this.colorData));
            this.colorData = null;
        }
        return this.lazy;
    }

    @Override
    public void close() {
        super.close();
        if (lazy != null) {
            this.lazy.close();
        }
    }
}
