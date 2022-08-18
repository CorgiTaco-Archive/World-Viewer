package com.example.examplemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;

import java.util.function.Function;

public class Tile {
    public final DynamicTexture texture;
    private final int worldX;
    private final int worldZ;
    private final int size;


    public Tile(int ySample, int worldX, int worldZ, int size, Function<BlockPos, Integer> blockPosHolderFunction) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;

        NativeImage nativeImage = new NativeImage(size, size, false);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                worldPos.set(worldX + (x), ySample, worldZ + (z));
                Integer color = blockPosHolderFunction.apply(worldPos);
                nativeImage.setPixelRGBA(x, z, color);
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

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ) {
        RenderSystem.setShaderTexture(0, this.texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
    }
}
