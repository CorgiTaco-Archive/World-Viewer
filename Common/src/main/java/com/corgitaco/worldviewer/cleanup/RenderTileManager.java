package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.RenderTile;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.example.examplemod.platform.Services;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

public class RenderTileManager {
    private final ExecutorService executorService = createExecutor();

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<RenderTile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();
    private final ServerLevel level;
    private final BlockPos origin;

    public final Map<Long, RenderTile> rendering = new ConcurrentHashMap<>();

    public int submittedTaskCoolDown = 0;

    private final DataTileManager tileManager;

    public RenderTileManager(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
        tileManager = new DataTileManager(Services.PLATFORM.configDir().resolve(String.valueOf(level.getSeed())),level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(), level, level.getSeed());
    }

    public void tick(WorldScreenv2 worldScreenv2) {
        long originTile = worldScreenv2.tileKey(this.origin);
        int xTileRange = worldScreenv2.getXTileRange();
        int zTileRange = worldScreenv2.getZTileRange();

        int slices = 360;
        double sliceSize = Mth.TWO_PI / slices;

        int tileRange = Math.max(xTileRange, zTileRange) + 2;
        for (int tileDistanceFromOrigin = 0; tileDistanceFromOrigin <= tileRange; tileDistanceFromOrigin++) {

            int originWorldX = worldScreenv2.getWorldXFromTileKey(originTile) + (worldScreenv2.tileSize / 2);
            int originWorldZ = worldScreenv2.getWorldZFromTileKey(originTile) + (worldScreenv2.tileSize / 2);
            double distance = worldScreenv2.tileSize * tileDistanceFromOrigin;

            for (int i = 0; i < slices; i++) {
                double angle = i * sliceSize;
                int worldTileX = (int) Math.round(originWorldX + (Math.sin(angle) * distance));
                int worldTileZ = (int) Math.round(originWorldZ + (Math.cos(angle) * distance));
                if (worldScreenv2.worldViewArea.intersects(worldTileX, worldTileZ, worldTileX, worldTileZ)) {
                    long tilePos = LongPackingUtil.tileKey(worldScreenv2.blockToTile(worldTileX), worldScreenv2.blockToTile(worldTileZ));
                    if (!rendering.containsKey(tilePos)) {
                        trackedTileFutures.computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(() -> {
                            var x = worldScreenv2.getWorldXFromTileKey(tilePos);
                            var z = worldScreenv2.getWorldZFromTileKey(tilePos);

                            RenderTile renderTile = new RenderTile(this.tileManager, TileLayer.FACTORY_REGISTRY.get(), 63, x, z, worldScreenv2.tileSize, worldScreenv2.sampleResolution, worldScreenv2);
                            rendering.put(tilePos, renderTile);
                            return renderTile;
                        }, executorService));
                    }
                }
            }
        }


        LongSet toRemove = new LongOpenHashSet();
        trackedTileFutures.forEach((tilePos, future) -> {
            if (future.isCompletedExceptionally()) {
                try{
                    future.getNow(null);
                } catch (Exception e) {
                    e.printStackTrace();
//                    throw new RuntimeException(e);
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
            }
        });
        toRemove.forEach(this.trackedTileFutures::remove);
    }

    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks, WorldScreenv2 worldScreenv2) {
        ArrayList<RenderTile> renderTiles = new ArrayList<>(this.rendering.values());
        for (RenderTile tileToRender : renderTiles) {

            int localX = worldScreenv2.getLocalXFromWorldX(tileToRender.getTileWorldX());
            int localZ = worldScreenv2.getLocalZFromWorldZ(tileToRender.getTileWorldZ());

            int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
            int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);

            tileToRender.render(poseStack, screenTileMinX, screenTileMinZ, new ArrayList<>());
            tileToRender.afterTilesRender(poseStack, screenTileMinX, screenTileMinZ, new ArrayList<>());
        }
    }

    public void cull(WorldScreenv2 worldScreenv2) {

        LongSet toRemove = new LongOpenHashSet();
        this.rendering.forEach((pos, tile) -> {
            int x = tile.getTileWorldX();
            int z = tile.getTileWorldZ();
            if (!worldScreenv2.worldViewArea.intersects(x, z, x, z)) {
                tile.close();
                toRemove.add(pos);
            }
        });

        toRemove.forEach(rendering::remove);
    }

    public void close() {
        this.executorService.shutdownNow();
        this.rendering.forEach((pos, tile) -> tile.close());
        this.rendering.clear();
        this.tileManager.close();
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
}
