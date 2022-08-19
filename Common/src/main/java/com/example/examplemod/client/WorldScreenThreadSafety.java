package com.example.examplemod.client;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

// Class to manage thread-safety.
public final class WorldScreenThreadSafety {
    private static final long UNSIGNED_INT_BIT_MASK = 0xFFFFFFFF;

    private static final int BIT_MASK = 0xFF;

    // Concurrent - Non-blocking.

    // Blocking.
    private final Object2IntMap<Holder<Biome>> colors = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());

    private ExecutorService service = createExecutorService();

    public WorldScreenThreadSafety(ServerLevel level) {

        level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().parallelStream().forEach(holder -> {
            var random = ThreadLocalRandom.current();

            var r = random.nextInt(256);
            var g = random.nextInt(256);
            var b = random.nextInt(256);

            colors.put(holder, (255 << 24) | ((r & BIT_MASK) << 16) | ((g & BIT_MASK) << 8) | (b & BIT_MASK));
        });
    }

    // Deprecated Methods.

    @Deprecated
    public CompletableFuture<Tile> computeLazily(boolean useHeightmap, int y, int size, int resolution, ServerLevel level, int bitShift, long l) {
        return CompletableFuture.supplyAsync(() -> {
            var x = (int) ((l & UNSIGNED_INT_BIT_MASK) << bitShift);
            var z = (int) (((l >>> 32) & UNSIGNED_INT_BIT_MASK) << bitShift);

            return new Tile(useHeightmap, y, x, z, size, resolution, level, colors);
        }, service);
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
}
