package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.Map;

public class MixedLayer extends TileLayer {
    public MixedLayer(DataTileManager dataTileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen, LongSet loadedChunks) {
        super(dataTileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
    }


    @Override
    public void render(PoseStack stack, float opacity, Map<String, TileLayer> tileLayerMap) {
        RenderSystem.enableBlend();

        RenderSystem.setShader(() -> this.screen.renderTileManager.shaderInstance);
        this.screen.renderTileManager.shaderInstance.getUniform("Opacity").set(opacity);
        TileLayer heights = tileLayerMap.get("heights");
        TileLayer biomes = tileLayerMap.get("biomes");
        if (heights != null && biomes != null) {
            renderAndMixImage(stack, heights.getImage(), biomes.getImage(), this.size, opacity, 1);
        }
    }

    private void renderAndMixImage(PoseStack stack, DynamicTexture texture, DynamicTexture texture2, int size, float opacity, float brightness) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.setShaderTexture(1, texture2.getId());

        blit(stack, 0, 0, 0.0F, 0.0F, size, size, size, size);
    }


    public static void blit(PoseStack pPoseStack, int pX, int pY, int pWidth, int pHeight, float pUOffset, float pVOffset, int pUWidth, int pVHeight, int pTextureWidth, int pTextureHeight) {
        innerBlit(pPoseStack, pX, pX + pWidth, pY, pY + pHeight, 0, pUWidth, pVHeight, pUOffset, pVOffset, pTextureWidth, pTextureHeight);
    }

    public static void blit(PoseStack pPoseStack, int pX, int pY, float pUOffset, float pVOffset, int pWidth, int pHeight, int pTextureWidth, int pTextureHeight) {
        blit(pPoseStack, pX, pY, pWidth, pHeight, pUOffset, pVOffset, pWidth, pHeight, pTextureWidth, pTextureHeight);
    }

    private static void innerBlit(PoseStack pPoseStack, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, int pUWidth, int pVHeight, float pUOffset, float pVOffset, int pTextureWidth, int pTextureHeight) {
        innerBlit(pPoseStack.last().pose(), pX1, pX2, pY1, pY2, pBlitOffset, (pUOffset + 0.0F) / (float)pTextureWidth, (pUOffset + (float)pUWidth) / (float)pTextureWidth, (pVOffset + 0.0F) / (float)pTextureHeight, (pVOffset + (float)pVHeight) / (float)pTextureHeight);
    }


    private static void innerBlit(Matrix4f pMatrix, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, float pMinU, float pMaxU, float pMinV, float pMaxV) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(pMatrix, (float)pX1, (float)pY2, (float)pBlitOffset).uv(pMinU, pMaxV).endVertex();
        bufferbuilder.vertex(pMatrix, (float)pX2, (float)pY2, (float)pBlitOffset).uv(pMaxU, pMaxV).endVertex();
        bufferbuilder.vertex(pMatrix, (float)pX2, (float)pY1, (float)pBlitOffset).uv(pMaxU, pMinV).endVertex();
        bufferbuilder.vertex(pMatrix, (float)pX1, (float)pY1, (float)pBlitOffset).uv(pMinU, pMinV).endVertex();
        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);
    }
}
