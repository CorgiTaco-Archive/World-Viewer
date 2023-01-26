package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.client.WVDynamicTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class HeightsLayer extends TileLayer {

    @Nullable
    private DynamicTexture lazy;

    private int[][] colorData;

    public HeightsLayer(DataTileManager tileManager, int y, int worldX, int worldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, worldX, worldZ, size, sampleResolution, screen);


        int sampledSize = size / sampleResolution;
        colorData = new int[sampledSize][sampledSize];

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < sampledSize; sampleX++) {
            for (int sampleZ = 0; sampleZ < sampledSize; sampleZ++) {
                worldPos.set(worldX + (sampleX * sampleResolution), 0, worldZ + (sampleZ * sampleResolution));

                sampledChunks.add(ChunkPos.asLong(worldPos));

                y = tileManager.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());

                int grayScale = getGrayScale(y, tileManager.serverLevel());

                colorData[sampleX][sampleZ] = grayScale;
            }
        }
    }

    public static int getGrayScale(int y, LevelHeightAccessor heightAccessor) {
        float pct = Mth.clamp(Mth.inverseLerp(y, 0, 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(127, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public void render(PoseStack stack, float opacity, Map<String, TileLayer> layers) {
        if (!layers.containsKey("mixed_heights_biomes") && getImage() != null) {
            renderImage(stack, getImage(), opacity, 1);
        }
    }

    @Override
    public @Nullable DynamicTexture getImage() {
        if (lazy == null) {
            this.lazy = new WVDynamicTexture(makeNativeImageFromColorData(this.colorData));
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

    @Override
    public float brightness() {
        return 1;
    }
}
