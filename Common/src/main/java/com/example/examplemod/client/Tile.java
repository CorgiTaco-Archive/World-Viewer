package com.example.examplemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

import java.util.function.Function;

public class Tile {
    public final DynamicTexture texture;
    private final int worldX;
    private final int worldZ;
    private final int size;

    private final Holder<Biome>[][] biomeAtPos;

    public Tile(int ySample, int worldX, int worldZ, int size, Function<BlockPos, Holder<Biome>> blockPosHolderFunction, Object2IntOpenHashMap<Holder<Biome>> colorLookup) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;
        this.biomeAtPos = new Holder[size][size];
        NativeImage nativeImage = new NativeImage(size, size, false);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                worldPos.set(worldX - x, ySample, worldZ - z);
                Holder<Biome> biomeHolder = blockPosHolderFunction.apply(worldPos);
                biomeAtPos[x][z] = biomeHolder;
                nativeImage.setPixelRGBA(x, z, colorLookup.getOrDefault(biomeHolder, 0));
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

    public Holder<Biome> getBiomeAtMousePosition(int mouseX, int mouseZ, int screenTileMinX, int screenTileMinZ) {
        return this.biomeAtPos[mouseX - screenTileMinX][mouseZ - screenTileMinZ];
    }


    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ) {
        RenderSystem.setShaderTexture(0, this.texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
    }
}
