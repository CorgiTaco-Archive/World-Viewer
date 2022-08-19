package com.example.examplemod.client;

import com.example.examplemod.mixin.client.KeyMappingAccess;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.*;

import static com.example.examplemod.util.LongPackingUtil.getTileX;
import static com.example.examplemod.util.LongPackingUtil.getTileZ;

public class WorldScreen extends Screen {

    private ExecutorService executorService = createExecutor();


    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<Tile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();

    private final ServerLevel level;

    private final BlockPos.MutableBlockPos origin;

    private int scrollCooldown;

    private final LongSet submitted = new LongOpenHashSet();
    float scale = 0.5F;

    private int shift = 9;


    int tileSize = tileToBlock(1);

    // Concurrent to avoid locking the main thread and will be replaced with batching later on.
    private final Queue<Tile> toRender = new ConcurrentLinkedQueue<>();

    private BoundingBox worldViewArea;

    private boolean heightMap = false;

    private int sampleResolution = 16;

    public LongList tilesToSubmit = new LongArrayList();

    public int submittedTaskCoolDown = 0;

    // String formatting
    private final StringBuilder builder = new StringBuilder();

    private final Formatter formatter = new Formatter(builder);

    private boolean structuresNeedUpdates;

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = Collections.synchronizedMap(new HashMap<>());

    // Wip.
    private final WorldScreenThreadSafety threadSafety;

    public WorldScreen(Component $$0) {
        super($$0);
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Level.OVERWORLD);

        BlockPos playerBlockPos = Minecraft.getInstance().player.blockPosition();
        origin = new BlockPos.MutableBlockPos().set(playerBlockPos).setY(Mth.clamp(playerBlockPos.getY(), this.level.getMinBuildHeight(), this.level.getMaxBuildHeight()));
        setWorldArea();
        this.structuresNeedUpdates = true;

        threadSafety = new WorldScreenThreadSafety(level);
    }


    private void setWorldArea() {
        int screenCenterX = getScreenCenterX();
        int screenCenterZ = getScreenCenterZ();

        int xRange = blockToTile(screenCenterX) + 2;
        int zRange = blockToTile(screenCenterZ) + 2;
        this.worldViewArea = BoundingBox.fromCorners(
                new Vec3i(
                        this.origin.getX() - tileToBlock(xRange) - 1,
                        level.getMinBuildHeight(),
                        this.origin.getZ() - tileToBlock(zRange) - 1
                ),
                new Vec3i(
                        this.origin.getX() + tileToBlock(xRange) + 1,
                        level.getMaxBuildHeight(),
                        this.origin.getZ() + tileToBlock(zRange) + 1
                )
        );
    }

    @Override
    public void tick() {
        this.scrollCooldown--;
        if (scrollCooldown < 0) {
            handleTileTracking();
        } else {
        }
        super.tick();
    }

    private void handleTileTracking() {
        long originChunk = tileKey(this.origin);
        int centerX = getScreenCenterX();
        int centerZ = getScreenCenterZ();

        int xRange = blockToTile(centerX) + 2;
        int zRange = blockToTile(centerZ) + 2;

        for (int x = -xRange; x <= xRange; x++) {
            for (int z = -zRange; z <= zRange; z++) {
                int worldTileX = getTileX(originChunk) + x;
                int worldTileZ = getTileZ(originChunk) + z;
                long worldChunk = LongPackingUtil.tileKey(worldTileX, worldTileZ);
                if (submitted.add(worldChunk)) {
                    tilesToSubmit.add(worldChunk);
                }
            }
        }

        if (submittedTaskCoolDown >= 0) {
            int tilesPerThreadCount = 2;
            int to = Math.min(tilesToSubmit.size(), tilesPerThreadCount);
            LongList tilePositions = tilesToSubmit.subList(0, to);

            for (long tilePos : tilePositions) {
                this.trackedTileFutures.computeIfAbsent(tilePos, key -> {
                    return threadSafety.computeLazily(heightMap, origin.getY(), tileSize, sampleResolution, level, shift, key);

                });
            }
            this.tilesToSubmit.removeElements(0, to);
        }

        LongList toRemove = new LongArrayList();
        trackedTileFutures.forEach((tilePos, future) -> {
            int worldX = getWorldXFromTileKey(tilePos);
            int worldZ = getWorldZFromTileKey(tilePos);
            if (this.worldViewArea.intersects(worldX, worldZ, worldX, worldZ)) {
                future.thenAcceptAsync(tile -> {
                    toRender.add(tile);

                    tile.getPositionsForStructure().forEach((holder, longs) -> {
                        positionsForStructure.computeIfAbsent(holder, key -> new LongArraySet()).addAll(longs);
                    });

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

    @Override
    public void render(PoseStack stack, int mouseX, int mouseZ, float partialTicks) {
        renderTiles(stack, mouseX, mouseZ);
        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    private void renderTiles(PoseStack stack, int mouseX, int mouseZ) {
        stack.pushPose();
        stack.scale(scale, scale, 0);

        int screenCenterX = getScreenCenterX();
        int screenCenterZ = getScreenCenterZ();

        int scaledMouseX = (int) (mouseX / scale);
        int scaledMouseZ = (int) (mouseZ / scale);

        int worldX = this.origin.getX() - (screenCenterX - scaledMouseX);
        int worldZ = this.origin.getZ() - (screenCenterZ - scaledMouseZ);

        builder.setLength(0);
        MutableComponent tooltip = new TextComponent(formatter.format("%s, %s, %s", worldX, "???", worldZ).toString());

        for (Tile tileToRender : this.toRender) {
            int localX = getLocalXFromWorldX(tileToRender.getWorldX());
            int localZ = getLocalZFromWorldZ(tileToRender.getWorldZ());

            int screenTileMinX = (screenCenterX + localX);
            int screenTileMinZ = (screenCenterZ + localZ);
            tileToRender.render(stack, screenTileMinX, screenTileMinZ);

            if (tileToRender.isMouseIntersecting(scaledMouseX, scaledMouseZ, screenTileMinX, screenTileMinZ)) {
                Tile.DataAtPosition dataAtPosition = tileToRender.getBiomeAtMousePosition(scaledMouseX, scaledMouseZ, screenTileMinX, screenTileMinZ);

                builder.setLength(0);
                var pos = dataAtPosition.worldPos();
                tooltip = new TextComponent(formatter.format("%s, %s, %s | ", pos.getX(), pos.getY(), pos.getZ()).toString()).append(getTranslationComponent(dataAtPosition.biomeHolder(), builder, formatter));
            }
        }

        this.positionsForStructure.forEach(((configuredStructureFeatureHolder, longs) -> {
            for (long structureChunkPos : longs) {
                int structureWorldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(structureChunkPos), 7);
                int structureWorldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(structureChunkPos), 7);

                if (worldViewArea.intersects(structureWorldX, structureWorldZ, structureWorldX, structureWorldZ)) {
                    int range = 24;

                    int drawX = screenCenterX + getLocalXFromWorldX(structureWorldX);
                    int drawZ = screenCenterZ + getLocalZFromWorldZ(structureWorldZ);

                    GuiComponent.fill(stack, drawX - range, drawZ - range, drawX + range, drawZ + range, FastColor.ARGB32.color(255, 255, 255, 255));
                }
            }
        }));


        stack.popPose();

        renderTooltip(stack, tooltip, mouseX, mouseZ);
    }

    @Override
    public void onClose() {
        threadSafety.close();

        if (!toRender.isEmpty()) {
            toRender.forEach(Tile::close);
        }

        executorService.shutdownNow();
        formatter.close();
        super.onClose();
    }


    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.origin.move((int) (dragX / scale), 0, (int) (dragY / scale));
        cull();
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void cull() {
        setWorldArea();

        toRender.removeIf(tile -> {
            int x = tile.getWorldX();
            int z = tile.getWorldZ();
            if (!worldViewArea.intersects(x, z, x, z)) {
                tile.close();
                submitted.remove(LongPackingUtil.tileKey(blockToTile(x), blockToTile(z)));

                return true;
            }

            return false;
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isKeyOrMouseButtonDown(this.minecraft, this.minecraft.options.keyShift)) {
            if (!this.level.isOutsideBuildHeight((int) (this.origin.getY() + delta))) {
                this.origin.move(0, (int) delta, 0);
                this.submitted.clear();

                executorService.shutdownNow();
                executorService = createExecutor();

                toRender.removeIf(tile -> {
                    tile.close();
                    return true;
                });
            }
        } else {
            this.scale = (float) Mth.clamp(this.scale + (delta * 0.05), 0.05, 1.5);
            cull();
        }
        this.scrollCooldown = 30;
        return true;
    }

    public static boolean isKeyOrMouseButtonDown(Minecraft minecraft, KeyMapping keyMapping) {
        InputConstants.Key key = ((KeyMappingAccess) keyMapping).wv_getKey();
        long window = minecraft.getWindow().getWindow();
        int keyValue = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, keyValue) == 1;
        } else {
            return InputConstants.isKeyDown(window, keyValue);
        }
    }

    public long tileKey(BlockPos pos) {
        return LongPackingUtil.tileKey(blockToTile(pos.getX()), blockToTile(pos.getZ()));
    }

    public int blockToTile(int blockCoord) {
        return LongPackingUtil.blockToTile(blockCoord, this.shift);
    }

    public int tileToBlock(int tileCoord) {
        return LongPackingUtil.tileToBlock(tileCoord, this.shift);
    }

    public int getScreenCenterX() {
        return (int) ((this.width / 2) / scale);
    }

    public int getScreenCenterZ() {
        return (int) ((this.height / 2) / scale);
    }

    public int getWorldXFromTileKey(long tileKey) {
        return tileToBlock(getTileX(tileKey));
    }

    public int getWorldZFromTileKey(long tileKey) {
        return tileToBlock(getTileZ(tileKey));
    }

    public int getTileLocalXFromWorldX(int worldX) {
        return getTileX(getOriginChunk()) - blockToTile(worldX);
    }

    public int getTileLocalZFromWorldZ(int worldZ) {
        return getTileZ(getOriginChunk()) - blockToTile(worldZ);
    }

    public long getOriginChunk() {
        return tileKey(this.origin);
    }

    public int getLocalXFromWorldX(int worldX) {
        return this.origin.getX() - worldX;
    }

    public int getLocalZFromWorldZ(int worldZ) {
        return this.origin.getZ() - worldZ;
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

    private static Component getTranslationComponent(Holder<Biome> holder, StringBuilder builder, Formatter formatter) {
        ResourceLocation location = holder.unwrapKey().orElseThrow().location();

        builder.setLength(0);
        String string = formatter.format("biome.%s.%s", location.getNamespace(), location.getPath()).toString();

        return Language.getInstance().has(string) ? new TranslatableComponent(string) : new TextComponent(location.toString());
    }
}