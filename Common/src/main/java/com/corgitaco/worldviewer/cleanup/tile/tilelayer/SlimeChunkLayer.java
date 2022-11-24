package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.common.WorldViewer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class SlimeChunkLayer extends TileLayer {

    @Nullable
    private final DynamicTexture dynamicTexture;

    public SlimeChunkLayer(@Nullable CompoundTag compoundTag, Map<String, Object> cache, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen) {
        super(compoundTag, cache, y, tileWorldX, tileWorldZ, size, sampleResolution, level, screen);
        @Nullable DynamicTexture dynamicTexture1;

        if (compoundTag != null) {
            try {
                dynamicTexture1 = new DynamicTexture(NativeImage.read(ByteBuffer.wrap(compoundTag.getByteArray("slime_chunks"))));
            } catch (IOException e) {
                e.printStackTrace();
                WorldViewer.LOGGER.error(String.format("Could not read Slime Chunks on disk. For tile {%s, %s}", tileWorldX, tileWorldZ));
                dynamicTexture1 = new DynamicTexture(getDynamicTexture(tileWorldX, tileWorldZ, size, level));
            }
        } else {
            dynamicTexture1 = new DynamicTexture(getDynamicTexture(tileWorldX, tileWorldZ, size, level));
        }
        this.dynamicTexture = dynamicTexture1;
    }

    private NativeImage getDynamicTexture(int tileWorldX, int tileWorldZ, int size, ServerLevel level) {
        NativeImage lazySlimeChunks = null;

        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) - x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) - z;
                boolean isSlimeChunk = WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, level.getSeed(), 987234911L).nextInt(10) == 0;

                if (isSlimeChunk) {
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
    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        if (this.dynamicTexture != null && this.dynamicTexture.getPixels() != null) {
            try {
                compoundTag.putByteArray("slime_chunks", this.dynamicTexture.getPixels().asByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return compoundTag;
    }

    @Override
    @Nullable
    public Component toolTip(double mouseX, double mouseY, double worldX, double worldZ) {
        return null;
    }

    @Override
    public void afterTilesRender(PoseStack stack, double mouseX, double mouseY, double worldX, double worldZ) {

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
