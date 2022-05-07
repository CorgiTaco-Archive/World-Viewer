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
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Function;

import static com.example.examplemod.client.WorldViewingScreen.*;

public class Tile {
    private final BoundingBox worldBoundingBox;

    public final WorldViewingScreen.DataAtPosition[][] dataAtPositions;

    public final DynamicTexture texture;
    private final int textureSizeWidth;
    private final int textureSizeHeight;

    public Tile(Object2IntOpenHashMap<Holder<Biome>> color, int worldTileX, int worldTileZ, Function<BlockPos, Holder<Biome>> biomeGetter) {
        BoundingBox boundingBox = new BoundingBox(
            tileToBlock(worldTileX), 0, tileToBlock(worldTileZ),
            tileToMaxBlock(worldTileX), 0, tileToMaxBlock(worldTileZ)
        );
        textureSizeWidth = boundingBox.getXSpan() - 1;
        textureSizeHeight = boundingBox.getZSpan() - 1;
        NativeImage image = new NativeImage(textureSizeWidth, textureSizeHeight, false);


        this.worldBoundingBox = boundingBox;
        dataAtPositions = new WorldViewingScreen.DataAtPosition[textureSizeWidth][textureSizeHeight];
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < textureSizeWidth; x++) {
            for (int z = 0; z < textureSizeHeight; z++) {
                mutableBlockPos.set(x + boundingBox.minX(), 63, z + boundingBox.minZ());
                Holder<Biome> biomeHolder = biomeGetter.apply(mutableBlockPos);
                dataAtPositions[x][z] = new WorldViewingScreen.DataAtPosition(color.getInt(biomeHolder), getKey(biomeHolder.unwrapKey().orElseThrow().location()));
                image.setPixelRGBA(x, z, color.getInt(biomeHolder));
            }
        }
        this.texture = new DynamicTexture(image);
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ) {
        RenderSystem.setShaderTexture(0, this.texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.textureSizeWidth, this.textureSizeHeight, this.textureSizeWidth, this.textureSizeHeight);
        RenderSystem.disableBlend();
    }
}