package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.client.WVDynamicTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private DynamicTexture lazy;

    @Nullable
    private int[][] colorData;

    public SlimeChunkLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet sampledChunks) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
        this.colorData = sampleResolution <= 16 && size <= 128 ? getColorData(tileManager, tileWorldX, tileWorldZ, size, sampledChunks) : null;
    }

    @Override
    public void render(PoseStack stack, float opacity, Map<String, TileLayer> layers) {
        if (getImage() != null) {
            renderImage(stack, getImage(), opacity, brightness());
        }
    }

    private static int[][] getColorData(DataTileManager tileManager, int tileWorldX, int tileWorldZ, int size, LongSet sampledChunks) {
        int[][] colorData = new int[size][size];
        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) + x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) + z;

                sampledChunks.add(ChunkPos.asLong(chunkX, chunkZ));
                if (tileManager.isSlimeChunk(chunkX, chunkZ)) {
                    for (int xMove = 0; xMove < 16; xMove++) {
                        for (int zMove = 0; zMove < 16; zMove++) {
                            if (xMove <= 1 || xMove >= 14 || zMove <= 1 || zMove >= 14) {
                                int dataX = SectionPos.sectionToBlockCoord(x) + xMove;
                                int dataZ = SectionPos.sectionToBlockCoord(z) + zMove;
                                colorData[dataX][dataZ] = BiomeLayer._ARGBToABGR(FastColor.ARGB32.color(255, 120, 190, 93));
                            } else {
                                BiomeLayer._ARGBToABGR(FastColor.ARGB32.color(0, 0, 0, 0));
                            }
                        }
                    }
                } else {
                    for (int xMove = 0; xMove < 16; xMove++) {
                        for (int zMove = 0; zMove < 16; zMove++) {
                            int dataX = SectionPos.sectionToBlockCoord(x) + xMove;
                            int dataZ = SectionPos.sectionToBlockCoord(z) + zMove;
                            colorData[dataX][dataZ] = BiomeLayer._ARGBToABGR(FastColor.ARGB32.color(0, 0, 0, 0));
                        }
                    }
                }
            }
        }
        return colorData;
    }

    @Override
    @Nullable
    public DynamicTexture getImage() {
        if (this.lazy == null && this.colorData != null) {
            this.lazy = new WVDynamicTexture(makeNativeImageFromColorData(this.colorData));
            this.colorData = null;
        }
        return this.lazy;
    }

    @Override
    public void close() {
        if (this.lazy != null) {
            this.lazy.close();
        }
    }
}
