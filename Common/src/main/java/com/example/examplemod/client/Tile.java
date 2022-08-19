package com.example.examplemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

public class Tile {
    public final DynamicTexture texture;
    private final int worldX;
    private final int worldZ;
    private final int size;

    private final DataAtPosition[][] dataAtPos;

    public Tile(boolean heightMap, int ySample, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level, Object2IntOpenHashMap<Holder<Biome>> colorLookup) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;
        this.dataAtPos = new DataAtPosition[size][size];
        NativeImage nativeImage = new NativeImage(size, size, false);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, ySample, worldZ - sampleZ);
                Holder<Biome> biomeHolder = level.getBiome(worldPos);
                int y = ySample;
                if (heightMap) {
                    boolean hasChunk = level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()));
                    if (hasChunk) {
                        y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());
                    } else {
                        y = level.getChunkSource().getGenerator().getBaseHeight(worldPos.getX(), worldPos.getZ(), Heightmap.Types.OCEAN_FLOOR, level);
                    }
                }

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;
                        dataAtPos[dataX][dataZ] = new DataAtPosition(biomeHolder, new BlockPos(worldPos.getX(), y, worldPos.getZ()));
                        nativeImage.setPixelRGBA(dataX, dataZ, colorLookup.getOrDefault(biomeHolder, 0));

                    }
                }
            }
        }
        this.texture = new DynamicTexture(nativeImage);
    }


    public int getWorldX() {
        return worldX;
    }

    public int getWorldZ() {
        return worldZ;
    }

    public boolean isMouseIntersecting(int scaledMouseX, int scaledMouseZ, int screenTileMinX, int screenTileMinZ) {
        boolean greaterThanMinX = scaledMouseX >= screenTileMinX;
        boolean greaterThanMinZ = scaledMouseZ >= screenTileMinZ;
        boolean lessThanMaxX = scaledMouseX < screenTileMinX + size;
        boolean lessThanMaxZ = scaledMouseZ < screenTileMinZ + size;
        return greaterThanMinX && greaterThanMinZ && lessThanMaxX && lessThanMaxZ;
    }

    public DataAtPosition getBiomeAtMousePosition(int mouseX, int mouseZ, int screenTileMinX, int screenTileMinZ) {
        return this.dataAtPos[mouseX - screenTileMinX][mouseZ - screenTileMinZ];
    }


    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ) {
        RenderSystem.setShaderTexture(0, this.texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
    }

    record DataAtPosition(Holder<Biome> biomeHolder, BlockPos worldPos) {
    }
}
