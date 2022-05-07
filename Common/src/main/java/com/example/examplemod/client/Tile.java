package com.example.examplemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.function.Function;

import static com.example.examplemod.client.WorldViewingScreen.*;

public class Tile {

    public final WorldViewingScreen.DataAtPosition[][] dataAtPositions;

    public final DynamicTexture texture;
    private final int textureSizeWidth;
    private final int textureSizeHeight;

    public Tile(int precision, int worldTileX, int y, int worldTileZ, Function<BlockPos, DataAtPosition> biomeGetter) {
        BoundingBox boundingBox = new BoundingBox(
            tileToBlock(worldTileX), y, tileToBlock(worldTileZ),
            tileToMaxBlock(worldTileX), y, tileToMaxBlock(worldTileZ)
        );
        textureSizeWidth = boundingBox.getXSpan() - 1;
        textureSizeHeight = boundingBox.getZSpan() - 1;
        NativeImage image = new NativeImage(textureSizeWidth, textureSizeHeight, false);


        dataAtPositions = new WorldViewingScreen.DataAtPosition[textureSizeWidth][textureSizeHeight];
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        for (int zoomedX = 0; zoomedX < textureSizeWidth; zoomedX += precision) {
            for (int zoomedZ = 0; zoomedZ < textureSizeHeight; zoomedZ += precision) {
                mutableBlockPos.set(zoomedX + boundingBox.minX(), y, zoomedZ + boundingBox.minZ());
                DataAtPosition dataAtZoomedPosition = biomeGetter.apply(mutableBlockPos);
                for (int x = 0; x < precision; x++) {
                    for (int z = 0; z < precision; z++) {
                        int dataX = zoomedX + x;
                        int dataZ = zoomedZ + z;

                        dataAtPositions[dataX][dataZ] = dataAtZoomedPosition;
                        image.setPixelRGBA(dataX, dataZ, dataAtZoomedPosition.color());
                    }
                }
            }
        }
        for (int chunkX = 0; chunkX < tileToChunk(blockToTile(textureSizeWidth)); chunkX++) {
            for (int chunkZ = 0; chunkZ < tileToChunk(blockToTile(textureSizeWidth)); chunkZ++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                int chunkBoundaryColor = FastColor.ARGB32.color(255, 255, 255, 255);
                if (textureSizeWidth > chunkPos.getMinBlockX()) {
                    for (int xDraw = 0; xDraw < textureSizeHeight; xDraw++) {
                        image.setPixelRGBA(chunkPos.getMinBlockX(), xDraw, chunkBoundaryColor);
                    }
                }
                if (textureSizeHeight > chunkPos.getMinBlockZ()) {
                    for (int xDraw = 0; xDraw < textureSizeWidth; xDraw++) {
                        image.setPixelRGBA(xDraw, chunkPos.getMinBlockZ(), chunkBoundaryColor);
                    }
                }
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