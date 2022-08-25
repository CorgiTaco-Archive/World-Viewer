package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.cleanup.tile.TileV2;
import com.example.examplemod.util.LongPackingUtil;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
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

    public final List<TileV2> tiles = new ArrayList<>();

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

            }
            this.tilesToSubmit.removeElements(0, to);
        }

        Set<Long> toRemove = ConcurrentHashMap.newKeySet();
        trackedTileFutures.forEach((tilePos, future) -> {
            int worldX = worldScreenv2.getWorldXFromTileKey(tilePos);
            int worldZ = worldScreenv2.getWorldZFromTileKey(tilePos);
            if (worldScreenv2.worldViewArea.intersects(worldX, worldZ, worldX, worldZ)) {
                future.thenAcceptAsync(tile -> {
//                    toRender.add(tile);
                    toRemove.add(tilePos);
                }, executorService);

            } else if (!future.isCancelled()) {
                future.cancel(true);

                toRemove.add(tilePos);
                this.submitted.remove(tilePos);
            }
        });
        toRemove.forEach(this.trackedTileFutures::remove);
    }

    public void render() {
        for (TileV2 tile : this.tiles) {
            
        }
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
