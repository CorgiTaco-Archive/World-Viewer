package com.example.examplemod.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.mojang.blaze3d.systems.RenderSystem.*;
import static net.minecraft.client.gui.GuiComponent.blit;

// No batching. TODO: Finish.
public final class FastTile implements AutoCloseable {
    private final DynamicTexture texture;

    private final int x;
    private final int z;

    private final int width;
    private final int height;

    public FastTile(ServerLevel level, ExecutorService service, boolean useHeightmap, int width, int height, int x, int y, int z, int resolution, Object2IntMap<Holder<Biome>> map) throws ExecutionException, InterruptedException {
        this.x = x;
        this.z = z;

        this.width = width;
        this.height = height;

        texture = CompletableFuture.supplyAsync(() -> {
            var pixels = new NativeImage(width, height, true);

            var mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int j = 0; j < width; j += resolution) {
                for (int k = 0; k < height; k += resolution) {
                    mutableBlockPos.set(x + j, y, z + k);

                    var holder = level.getBiome(mutableBlockPos);

                    int l = y;
                    if (useHeightmap) {
                        var source = level.getChunkSource();

                        var type = Heightmap.Types.OCEAN_FLOOR;

                        l = source.hasChunk(
                                SectionPos.blockToSectionCoord(mutableBlockPos.getX()),
                                SectionPos.blockToSectionCoord(mutableBlockPos.getZ())) ?
                                level.getHeight(type, mutableBlockPos.getX(), mutableBlockPos.getZ()) : source.getGenerator().getBaseHeight(mutableBlockPos.getX(), mutableBlockPos.getZ(), type, level);
                    }

                    for (int m = 0; m < resolution; m++) {
                        for (int n = 0; n < resolution; n++) {
                            var pixelX = j + m;
                            var pixelZ = k + n;

                            pixels.setPixelRGBA(pixelX, pixelZ, map.getOrDefault(holder, 0));
                        }
                    }
                }
            }

            return new DynamicTexture(pixels);
        }, service).join();
    }

    public void draw(PoseStack stack, int minX, int minZ) {
        setShaderTexture(0, texture.getId());
        blendFunc(GlStateManager.SourceFactor.SRC_ALPHA.value, GlStateManager.SourceFactor.ONE_MINUS_SRC_ALPHA.value);
        enableBlend();
        blit(stack, minX, minZ, 0.0F, 0.0F, width, height, width, height);
        disableBlend();
    }

    public boolean doesMouseIntersect(float mouseX, float mouseZ, float minX, float minZ) {
        var x = mouseX >= minX && mouseX < minZ + width;
        var z = mouseZ >= minZ && mouseZ < minZ + height;

        return x && z;
    }

    @Override
    public void close() {
        texture.close();
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }
}
