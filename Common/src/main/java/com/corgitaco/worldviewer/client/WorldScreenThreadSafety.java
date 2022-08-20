package com.corgitaco.worldviewer.client;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectLongPair;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

// Class to manage thread-safety.
public final class WorldScreenThreadSafety {
    private static final long UNSIGNED_INT_BIT_MASK = 0xFFFFFFFF;

    // Concurrent - Non-blocking.
    private final Queue<ObjectLongPair<CompletableFuture<Tile>>> pairs = new ConcurrentLinkedQueue<>();

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> map = new ConcurrentHashMap<>();

    private ExecutorService service = createExecutorService();

    WorldScreenThreadSafety() {
    }

    // Deprecated Methods.

    @Deprecated
    public void computeLazily(boolean useHeightmap, int y, int size, int resolution, ServerLevel level, int bitShift, long l, Object2IntMap<Holder<Biome>> colors) {
        pairs.offer(ObjectLongPair.of(createCompletableFuture(useHeightmap, y, size, resolution, level, bitShift, l, colors), l));
    }

    @Deprecated
    public CompletableFuture<Tile> createCompletableFuture(boolean useHeightmap, int y, int size, int resolution, ServerLevel level, int bitShift, long l, Object2IntMap<Holder<Biome>> colors) {
        return CompletableFuture.supplyAsync(() -> {
            var x = getX(l, bitShift);
            var z = getZ(l, bitShift);

            return new Tile(useHeightmap, y, x, z, size, resolution, level, colors);
        }, service);
    }

    @Deprecated
    public void queueForRendering(Queue<Tile> queue, BoundingBox frustum, int bitShift) {
        pairs.removeIf(pair -> {
            var future = pair.left();
            var l = pair.rightLong();

            var x = getX(l, bitShift);
            var z = getZ(l, bitShift);

            if (frustum.intersects(x, z, x, z)) {
                future.thenAcceptAsync(tile -> {
                    queue.offer(tile);

                    tile.getPositionsForStructure().forEach((holder, longs) -> {
                        map.computeIfAbsent(holder, unused -> new LongArraySet()).addAll(longs);
                    });

                    pairs.remove(pair);
                }, service);

            } else if (!future.isCancelled()) {
                future.cancel(true);

                return true;
            }

            return false;
        });
    }

    public void computeLazilyBatched() {
        // TODO: Write when batching is done.
    }

    public void restartService() {
        service.shutdownNow();
        service = createExecutorService();
    }

    public void close() {
        service.shutdownNow();
    }

    // Create daemon threads.
    private static ExecutorService createExecutorService() {
        return Executors.newFixedThreadPool(4, new ThreadFactory() {
            private final ThreadFactory factory = Executors.defaultThreadFactory();

            @Override
            public Thread newThread(@NotNull Runnable r) {
                var thread = factory.newThread(r);
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private static int getX(long l, int bitShift) {
        return (int) ((l & UNSIGNED_INT_BIT_MASK) << bitShift);
    }

    private static int getZ(long l, int bitShift) {
        return (int) (((l >>> 32) & UNSIGNED_INT_BIT_MASK) << bitShift);
    }
}