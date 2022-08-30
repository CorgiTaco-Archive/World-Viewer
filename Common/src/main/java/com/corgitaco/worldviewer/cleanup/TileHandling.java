package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.cleanup.tile.TileV2;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.*;

import static com.example.examplemod.util.LongPackingUtil.getTileX;
import static com.example.examplemod.util.LongPackingUtil.getTileZ;

public class TileHandling {
    private ExecutorService executorService = createExecutor();

    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<TileV2>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();
    private final ServerLevel level;
    private final BlockPos origin;

    public LongList tilesToSubmit = new LongArrayList();

    public final Set<TileV2> tiles = ConcurrentHashMap.newKeySet();

    public int submittedTaskCoolDown = 0;

    private final LongSet submitted = new LongOpenHashSet();

    public TileHandling(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    public void tick(WorldScreenv2 worldScreenv2) {
        long originTile = worldScreenv2.tileKey(this.origin);

        int xTileRange = worldScreenv2.getXTileRange();
        int zTileRange = worldScreenv2.getZTileRange();

        for (int x = -xTileRange; x <= xTileRange; x++) {
            for (int z = -zTileRange; z <= zTileRange; z++) {
                int worldTileX = getTileX(originTile) + x;
                int worldTileZ = getTileZ(originTile) + z;
                long worldTile = LongPackingUtil.tileKey(worldTileX, worldTileZ);
                if (submitted.add(worldTile)) {
                    tilesToSubmit.add(worldTile);
                }
            }
        }

        if (submittedTaskCoolDown >= 0) {
            int tilesPerThreadCount = 2;
            int to = Math.min(tilesToSubmit.size(), tilesPerThreadCount);
            LongList tilePositions = tilesToSubmit.subList(0, to);

            for (long tilePos : tilePositions) {
              trackedTileFutures.computeIfAbsent(tilePos, key -> CompletableFuture.supplyAsync(() -> {
                    var x = worldScreenv2.getWorldXFromTileKey(tilePos);
                    var z = worldScreenv2.getWorldZFromTileKey(tilePos);

                    return new TileV2(TileLayer.FACTORY_REGISTRY, 63, x, z, worldScreenv2.tileSize, worldScreenv2.sampleResolution, worldScreenv2);
                }, executorService));
            }
            this.tilesToSubmit.removeElements(0, to);
        }

        Set<Long> toRemove = ConcurrentHashMap.newKeySet();
        trackedTileFutures.forEach((tilePos, future) -> {
            int worldX = worldScreenv2.getWorldXFromTileKey(tilePos);
            int worldZ = worldScreenv2.getWorldZFromTileKey(tilePos);
            if (worldScreenv2.worldViewArea.intersects(worldX, worldZ, worldX, worldZ)) {
                future.thenAcceptAsync(tile -> {
                    tiles.add(tile);
                    toRemove.add(tilePos);
                }, executorService);

            }
            else if (!future.isCancelled()) {
                future.cancel(true);
                toRemove.add(tilePos);
                this.submitted.remove(tilePos);
            }
        });
        toRemove.forEach(this.trackedTileFutures::remove);
    }

    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks, WorldScreenv2 worldScreenv2) {
        ArrayList<TileV2> tileV2s = new ArrayList<>(this.tiles);
        for (TileV2 tileToRender : tileV2s) {

            int localX = worldScreenv2.getLocalXFromWorldX(tileToRender.getTileWorldX());
            int localZ = worldScreenv2.getLocalZFromWorldZ(tileToRender.getTileWorldZ());

            int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
            int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);

            tileToRender.render(poseStack, screenTileMinX, screenTileMinZ, new ArrayList<>());
        }

        for (TileV2 tileToRender : this.tiles) {

            int localX = worldScreenv2.getLocalXFromWorldX(tileToRender.getTileWorldX());
            int localZ = worldScreenv2.getLocalZFromWorldZ(tileToRender.getTileWorldZ());

            int screenTileMinX = (worldScreenv2.getScreenCenterX() + localX);
            int screenTileMinZ = (worldScreenv2.getScreenCenterZ() + localZ);

            tileToRender.afterTilesRender(poseStack, screenTileMinX, screenTileMinZ, new ArrayList<>());
        }
    }

    public void cull(WorldScreenv2 worldScreenv2) {
        this.tiles.removeIf(tile -> {
            int x = tile.getTileWorldX();
            int z = tile.getTileWorldZ();
            if (!worldScreenv2.worldViewArea.intersects(x, z, x, z)) {
                tile.close();
                submitted.remove(LongPackingUtil.tileKey(worldScreenv2.blockToTile(x),worldScreenv2. blockToTile(z)));

                return true;
            }

            return false;
        });
    }

    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(4, new ThreadFactory() {
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
