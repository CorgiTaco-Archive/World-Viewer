package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.tile.TileV2;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class HeightsLayer extends TileLayer {

    private final DynamicTexture heights;


    public HeightsLayer(@Nullable CompoundTag tag, Map<String, Object> cache, int y, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen) {
        super(tag, cache, y, worldX, worldZ, size, sampleResolution, level, screen);

        NativeImage image = new NativeImage(size, size, true);
        Long2IntOpenHashMap heights = (Long2IntOpenHashMap) cache.computeIfAbsent("heights", o -> new Long2IntOpenHashMap());

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, 0, worldZ - sampleZ);

                y = heights.computeIfAbsent(ChunkPos.asLong(sampleX, sampleZ), aLong -> {
                    boolean hasChunk = level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()));
                    if (hasChunk) {
                        return level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());
                    } else {
                        return generator.getBaseHeight(worldPos.getX(), worldPos.getZ(), Heightmap.Types.OCEAN_FLOOR, level);
                    }
                });

                int grayScale = getGrayScale(y, generator);

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

    public static int getGrayScale(int y, ChunkGenerator generator) {
        float pct = Mth.clamp(Mth.inverseLerp(y, generator.getMinY(), 255), 0, 1F);
        int color = Math.round(Mth.clampedLerp(128, 255, pct));
        return FastColor.ARGB32.color(255, color, color, color);
    }

    @Override
    public boolean canRender(TileV2 tileV2, Collection<String> currentlyRendering) {
        return !currentlyRendering.contains("biomes");
    }

    @Override
    public CompoundTag save() {
        return new CompoundTag();
    }

    @Override
    public void afterTilesRender(PoseStack stack, double mouseX, double mouseY, double mouseWorldX, double mouseWorldZ) {

    }

    @Override
    public @Nullable DynamicTexture getImage() {
        return this.heights;
    }
}
