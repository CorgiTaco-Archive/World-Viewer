package com.example.examplemod.client;

import com.example.examplemod.mixin.UtilAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class WorldScreen extends Screen {


    private static final ExecutorService EXECUTOR_SERVICE = UtilAccess.invokeMakeExecutor("world-viewer");


    List<CompletableFuture<List<Tile>>> tiles = new ArrayList<>();

    private final ServerLevel level;

    private BlockPos origin;

    private int resolution;

    private LongSet submitted = new LongOpenHashSet();

    Object2IntOpenHashMap<Holder<Biome>> colorForBiome = new Object2IntOpenHashMap<>();
    float scale = 0.3F;
    int size = 16;

    private final List<Tile> toRender = new ArrayList<>();


    public WorldScreen(Component $$0) {
        super($$0);
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Level.OVERWORLD);

        for (Holder<Biome> possibleBiome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            colorForBiome.put(possibleBiome, FastColor.ARGB32.color(255, level.random.nextInt(256), level.random.nextInt(256), level.random.nextInt(256)));
        }

        origin = Minecraft.getInstance().player.blockPosition();


    }

    @Override
    public void tick() {
        long originChunk = ChunkPos.asLong(this.origin);
        int centerX = (int) ((this.width / 2) / scale);
        int centerZ = (int) ((this.height / 2) / scale);

        int xRange = SectionPos.blockToSectionCoord(centerX) + 1;
        int zRange = SectionPos.blockToSectionCoord(centerZ) + 1;

        LongList tilesToSubmit = new LongArrayList();
        for (int x = -xRange; x <= xRange; x++) {
            for (int z = -zRange; z <= zRange; z++) {
                int worldChunkX = ChunkPos.getX(originChunk) + x;
                int worldChunkZ = ChunkPos.getZ(originChunk) + z;
                long worldChunk = ChunkPos.asLong(worldChunkX, worldChunkZ);
                if (submitted.add(worldChunk)) {
                    tilesToSubmit.add(worldChunk);
                }
            }
        }

        int tilesPerThreadCount = 100;
        for (int i = 0; i < tilesToSubmit.size(); i += tilesPerThreadCount) {
            LongList longList = tilesToSubmit.subList(i, Math.min(tilesToSubmit.size(), i + tilesPerThreadCount));
            LongList tilesForFuture = new LongArrayList();
            tilesForFuture.addAll(longList);
            this.tiles.add(CompletableFuture.supplyAsync(
                    () -> {
                        ArrayList<Tile> tiles = new ArrayList<>();
                        tilesForFuture.forEach(key -> tiles.add(new Tile(63, SectionPos.sectionToBlockCoord(ChunkPos.getX(key)), SectionPos.sectionToBlockCoord(ChunkPos.getZ(key)), size, blockPos -> this.colorForBiome.getInt(this.level.getBiome(blockPos)))));
                        return tiles;
                    },
                    EXECUTOR_SERVICE)
            );
        }


        List<CompletableFuture<List<Tile>>> completableFutures = this.tiles;
        for (int i = 0; i < completableFutures.size(); i++) {
            CompletableFuture<List<Tile>> tileList = completableFutures.get(i);
            List<Tile> tileListNow = tileList.getNow(null);
            if (tileListNow != null) {
                this.toRender.addAll(tileListNow);
                this.tiles.remove(i);
            }

        }
        super.tick();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseZ, float partialTicks) {
        long originChunk = ChunkPos.asLong(this.origin);

        stack.pushPose();
        stack.scale(scale, scale, 0);

        int centerX = (int) ((this.width / 2) / scale);
        int centerZ = (int) ((this.height / 2) / scale);

        for (Tile tile : this.toRender) {
            int localX = ChunkPos.getX(originChunk) - SectionPos.blockToSectionCoord(tile.getWorldX());
            int localZ = ChunkPos.getZ(originChunk) - SectionPos.blockToSectionCoord(tile.getWorldZ());

            int screenTileMinX = (centerX + localX * size);
            int screenTileMinZ = (centerZ + localZ * size);
            tile.render(stack, screenTileMinX, screenTileMinZ);
        }


        stack.popPose();
        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    @Override
    public void onClose() {
        for (CompletableFuture<List<Tile>> tile : this.tiles) {
            tile.cancel(true);
        }
        super.onClose();
    }
}