package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private final DynamicTexture dynamicTexture;

    public SlimeChunkLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
        this.dynamicTexture = new DynamicTexture(getDynamicTexture(tileManager, tileWorldX, tileWorldZ, size));
    }

    private NativeImage getDynamicTexture(DataTileManager tileManager, int tileWorldX, int tileWorldZ, int size) {
        NativeImage lazySlimeChunks = null;

        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) - x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) - z;

                if (tileManager.isSlimeChunk(chunkX, chunkZ)) {
                    if (lazySlimeChunks == null) {
                        lazySlimeChunks = new NativeImage(size, size, true);
                        lazySlimeChunks.fillRect(0, 0, size, size, FastColor.ARGB32.color(0, 0, 0, 0));
                    }

                    int dataX = SectionPos.sectionToBlockCoord(x);
                    int dataZ = SectionPos.sectionToBlockCoord(z);

                    lazySlimeChunks.fillRect(dataX, dataZ, 16, 16, FastColor.ARGB32.color(255, 120, 190, 93));
                }
            }
        }
        return lazySlimeChunks;
    }

    @Override
    @Nullable
    public DynamicTexture getImage() {
        return this.dynamicTexture;
    }

    @Override
    public void close() {
        if (this.dynamicTexture != null) {
            this.dynamicTexture.close();
        }
    }
}
