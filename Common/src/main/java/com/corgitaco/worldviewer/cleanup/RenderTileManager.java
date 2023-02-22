package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.WVVertexFormats;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.RenderTile;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.example.examplemod.platform.Services;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Pair;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import io.netty.util.internal.ConcurrentSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class RenderTileManager {
    private ExecutorService executorService = createExecutor();

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<RenderTile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();
    private WorldScreenv2 worldScreenv2;
    private final ServerLevel level;
    private final BlockPos origin;

    public final Map<Long, RenderTile> rendering = new ConcurrentHashMap<>();

    public final ConcurrentSet<RenderTile> toClose = new ConcurrentSet<>();


    private final DataTileManager tileManager;


    public boolean blockGeneration = true;

    public final ShaderInstance shaderInstance;
    public final ShaderInstance batchedShader;

    public RenderTileManager(WorldScreenv2 worldScreenv2, ServerLevel level, BlockPos origin) {
        this.worldScreenv2 = worldScreenv2;
        this.level = level;
        this.origin = origin;
        tileManager = new DataTileManager(Services.PLATFORM.configDir().resolve(String.valueOf(level.getSeed())), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());
        long originTile = worldScreenv2.tileKey(origin);
        loadTiles(worldScreenv2, originTile);
        try {
            shaderInstance = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "layer_mixer", DefaultVertexFormat.POSITION_TEX);
            batchedShader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), "batched", WVVertexFormats.POSITION_TEX_INDEX);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DataTileManager getDataTileManager() {
        return tileManager;
    }

    public void tick() {
        long originTile = worldScreenv2.tileKey(this.origin);
        if (!blockGeneration) {
            loadTiles(worldScreenv2, originTile);
            blockGeneration = true;
        }


        LongSet toRemove = new LongOpenHashSet();

        List<Runnable> toSubmit = new ArrayList<>();
        trackedTileFutures.forEach((tilePos, future) -> {
            if (future.isCompletedExceptionally()) {
                try {
                    future.getNow(null);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }

            if (future.isDone()) {
                toRemove.add(tilePos);
            }

            int worldX = worldScreenv2.getWorldXFromTileKey(tilePos);
            int worldZ = worldScreenv2.getWorldZFromTileKey(tilePos);
            if (!worldScreenv2.worldViewArea.intersects(worldX, worldZ, worldX, worldZ) && !future.isCancelled()) {
                future.cancel(true);
                toRemove.add(tilePos);
            } else {
                toSubmit.add(() -> {
                    RenderTile renderTile = future.getNow(null);
                    if (renderTile != null) {
                        int newSampleRes = renderTile.getSampleRes() >> 1;
                        if (newSampleRes >= worldScreenv2.sampleResolution) {
                            submitTileFuture(worldScreenv2, renderTile.getSize(), tilePos, newSampleRes, renderTile);
                        }
                    }
                });
            }
        });

        toRemove.forEach(this.trackedTileFutures::remove);

        toClose.removeIf(renderTile -> {
            renderTile.close(false);
            return true;
        });

        toSubmit.forEach(Runnable::run);
    }

    private void loadTiles(WorldScreenv2 worldScreenv2, long originTile) {
        int xTileRange = worldScreenv2.getXTileRange();
        int zTileRange = worldScreenv2.getZTileRange();

        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int tileRange = Math.max(xTileRange, zTileRange) + 2;
        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {

            int tileSize = worldScreenv2.tileSize;
            int originWorldX = worldScreenv2.getWorldXFromTileKey(originTile) + (tileSize / 2);
            int originWorldZ = worldScreenv2.getWorldZFromTileKey(originTile) + (tileSize / 2);
            double distance = tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (worldScreenv2.worldViewArea.intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    long tilePos = LongPackingUtil.tileKey(worldScreenv2.blockToTile(worldTileX), worldScreenv2.blockToTile(worldTileZ));
                    RenderTile tile = rendering.get(tilePos);
                    if (tile == null) {
                        submitTileFuture(worldScreenv2, tileSize, tilePos, worldScreenv2.sampleResolution << 3, null);
                    }
                }
            }
        }
    }

    private void submitTileFuture(WorldScreenv2 worldScreenv2, int tileSize, long tilePos, int sampleResolution, @Nullable RenderTile lastResolution) {
        trackedTileFutures.computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(() -> {
            var x = worldScreenv2.getWorldXFromTileKey(tilePos);
            var z = worldScreenv2.getWorldZFromTileKey(tilePos);

            RenderTile renderTile = new RenderTile(this.tileManager, TileLayer.FACTORY_REGISTRY, 63, x, z, tileSize, sampleResolution, worldScreenv2, lastResolution);
            RenderTile previous = rendering.put(tilePos, renderTile);
            if (previous != null && previous != renderTile) {
                this.toClose.add(previous);
            }
            return renderTile;
        }, executorService));
    }

    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks, WorldScreenv2 worldScreenv2) {
        ArrayList<RenderTile> renderTiles = new ArrayList<>(this.rendering.values());
        renderTiles(poseStack, worldScreenv2, renderTiles, worldScreenv2.opacities, RenderTile::render);
        renderTiles(poseStack, worldScreenv2, renderTiles, worldScreenv2.opacities, RenderTile::afterTilesRender);
    }

    private void renderTiles(PoseStack poseStack, WorldScreenv2 worldScreenv2, ArrayList<RenderTile> renderTiles, Map<String, Float> opacity, TileRenderStrategy tileRenderStrategy) {
        Map<String, Collection<Pair<DynamicTexture, Vector3f>>> tracked = new LinkedHashMap<>();
        for (RenderTile tileToRender : renderTiles) {

            int localX = (int) worldScreenv2.getLocalXFromWorldX(tileToRender.getTileWorldX());
            int localZ = (int) worldScreenv2.getLocalZFromWorldZ(tileToRender.getTileWorldZ());

            int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
            int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);


            tileToRender.getTileLayers().forEach((s, tileLayer) -> {
                DynamicTexture image = tileLayer.getImage();
                tracked.computeIfAbsent(s, key -> new ArrayList<>()).add(Pair.of(image, new Vector3f(screenTileMinX, screenTileMinZ, tileToRender.getSize())));
            });

        }

        tracked.forEach((s, pairs) -> {
            poseStack.pushPose();
            RenderSystem.setShader(() -> this.batchedShader);
            BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
            bufferbuilder.begin(VertexFormat.Mode.QUADS, WVVertexFormats.POSITION_TEX_INDEX);
            RenderSystem.setShaderColor(1, 1, 1, 1);

            this.batchedShader.getUniform("u_textures").set(new float[]{0, 1.0F, 2.0F, 3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F, 9.0F, 10.0F, 11.0F});

            int slot = 0;

            int maxTextureSlots = 12;

            for (Pair<DynamicTexture, Vector3f> pair : pairs) {
                DynamicTexture dynamicTexture = pair.getFirst();

                int size = (int) pair.getSecond().z();
                if (dynamicTexture != null) {

                    if (slot > maxTextureSlots) {
                        RenderSystem.setShaderColor(1, 1, 1, 1);

                        // End
                        bufferbuilder.end();
                        BufferUploader.end(bufferbuilder);

                        // Start again
                        RenderSystem.setShader(() -> this.batchedShader);
                        bufferbuilder = Tesselator.getInstance().getBuilder();
                        bufferbuilder.begin(VertexFormat.Mode.QUADS, WVVertexFormats.POSITION_TEX_INDEX);
                        RenderSystem.setShaderColor(1, 1, 1, 1);
                        slot = 0;
                    }
                    RenderSystem.setShaderTexture(slot, dynamicTexture.getId());


                    Vector3f renderCoords = pair.getSecond().copy();
//                    renderCoords.transform(Vector3f.ZN.rotationDegrees(180));

                    blit(poseStack, (int) renderCoords.x(), (int) renderCoords.y(), 0.0F, 0.0F, size, size, size, size, slot);
                    slot++;
                }
            }
            RenderSystem.setShaderColor(1, 1, 1, 1);

            bufferbuilder.end();
            BufferUploader.end(bufferbuilder);

            poseStack.popPose();
        });


    }

    record Vec2f(float x, float z) {
    }

    public static void blit(PoseStack pPoseStack, int pX, int pY, int pBlitOffset, int pWidth, int pHeight, TextureAtlasSprite pSprite, int textureIDX) {
        innerBlit(pPoseStack.last().pose(), pX, pX + pWidth, pY, pY + pHeight, pBlitOffset, pSprite.getU0(), pSprite.getU1(), pSprite.getV0(), pSprite.getV1(), textureIDX);
    }

    public void blit(PoseStack pPoseStack, int pX, int pY, int pUOffset, int pVOffset, int pUWidth, int pVHeight, int textureIDX) {
        blit(pPoseStack, pX, pY, 0, (float) pUOffset, (float) pVOffset, pUWidth, pVHeight, 256, 256, textureIDX);
    }

    public static void blit(PoseStack pPoseStack, int pX, int pY, int pBlitOffset, float pUOffset, float pVOffset, int pUWidth, int pVHeight, int pTextureHeight, int pTextureWidth, int textureIDX) {
        innerBlit(pPoseStack, pX, pX + pUWidth, pY, pY + pVHeight, pBlitOffset, pUWidth, pVHeight, pUOffset, pVOffset, pTextureHeight, pTextureWidth, textureIDX);
    }

    public static void blit(PoseStack pPoseStack, int pX, int pY, int pWidth, int pHeight, float pUOffset, float pVOffset, int pUWidth, int pVHeight, int pTextureWidth, int pTextureHeight, int textureIDX) {
        innerBlit(pPoseStack, pX, pX + pWidth, pY, pY + pHeight, 0, pUWidth, pVHeight, pUOffset, pVOffset, pTextureWidth, pTextureHeight, textureIDX);
    }

    public static void blit(PoseStack pPoseStack, int pX, int pY, float pUOffset, float pVOffset, int pWidth, int pHeight, int pTextureWidth, int pTextureHeight, int textureIDX) {
        blit(pPoseStack, pX, pY, pWidth, pHeight, pUOffset, pVOffset, pWidth, pHeight, pTextureWidth, pTextureHeight, textureIDX);
    }

    private static void innerBlit(PoseStack pPoseStack, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, int pUWidth, int pVHeight, float pUOffset, float pVOffset, int pTextureWidth, int pTextureHeight, int textureIDX) {
        innerBlit(pPoseStack.last().pose(), pX1, pX2, pY1, pY2, pBlitOffset, (pUOffset + 0.0F) / (float) pTextureWidth, (pUOffset + (float) pUWidth) / (float) pTextureWidth, (pVOffset + 0.0F) / (float) pTextureHeight, (pVOffset + (float) pVHeight) / (float) pTextureHeight, textureIDX);
    }

    private static void innerBlit(Matrix4f pMatrix, int pX1, int pX2, int pY1, int pY2, int pBlitOffset, float pMinU, float pMaxU, float pMinV, float pMaxV, int textureIDX) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        VertexConsumer consumer1 = bufferbuilder.vertex(pMatrix, (float) pX1, (float) pY2, (float) pBlitOffset).uv(pMinU, pMaxV);
        bufferbuilder.putFloat(0, textureIDX);
        bufferbuilder.nextElement();
        consumer1.endVertex();
        VertexConsumer consumer2 = bufferbuilder.vertex(pMatrix, (float) pX2, (float) pY2, (float) pBlitOffset).uv(pMaxU, pMaxV);
        bufferbuilder.putFloat(0, textureIDX);
        bufferbuilder.nextElement();
        consumer2.endVertex();
        VertexConsumer consumer3 = bufferbuilder.vertex(pMatrix, (float) pX2, (float) pY1, (float) pBlitOffset).uv(pMaxU, pMinV);
        bufferbuilder.putFloat(0, textureIDX);
        bufferbuilder.nextElement();
        consumer3.endVertex();
        VertexConsumer consumer4 = bufferbuilder.vertex(pMatrix, (float) pX1, (float) pY1, (float) pBlitOffset).uv(pMinU, pMinV);
        bufferbuilder.putFloat(0, textureIDX);
        bufferbuilder.nextElement();
        consumer4.endVertex();
    }


    public void cull(WorldScreenv2 worldScreenv2) {
        LongSet toRemove = new LongOpenHashSet();
        this.rendering.forEach((pos, tile) -> {
            int x = tile.getTileWorldX();
            int z = tile.getTileWorldZ();
            if (!worldScreenv2.worldViewArea.intersects(x, z, x, z)) {
                tile.close(true);
                toRemove.add(pos);
            }
        });

        toRemove.forEach(rendering::remove);
    }

    public void close() {
        this.executorService.shutdownNow();
        this.rendering.forEach((pos, tile) -> tile.close(true));
        this.rendering.clear();
        this.tileManager.close();
    }

    public void onScroll() {
        this.executorService.shutdownNow();
        this.executorService = createExecutor();
        this.trackedTileFutures.clear();
        this.rendering.forEach((pos, tile) -> tile.close(true));
        this.rendering.clear();
    }

    public static ExecutorService createExecutor() {
        return createExecutor(Mth.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 25));
    }

    public static ExecutorService createExecutor(int processors) {
        return Executors.newFixedThreadPool(processors, new ThreadFactory() {
            private final ThreadFactory backing = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r) {
                var thread = backing.newThread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @FunctionalInterface
    public interface TileRenderStrategy {

        void renderTile(RenderTile renderTile, PoseStack stack, int screenTileMinX, int screenTileMinZ, List<String> toRender, Map<String, Float> opacity);
    }
}
