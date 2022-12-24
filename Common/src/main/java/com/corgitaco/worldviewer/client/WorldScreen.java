package com.corgitaco.worldviewer.client;

import com.corgitaco.worldviewer.cleanup.tile.tilelayer.BiomeLayer;
import com.corgitaco.worldviewer.mixin.KeyMappingAccess;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.Formatter;
import java.util.Set;
import java.util.concurrent.*;

import static com.example.examplemod.util.LongPackingUtil.getTileX;
import static com.example.examplemod.util.LongPackingUtil.getTileZ;
import static org.lwjgl.opengl.GL11.*;

public final class WorldScreen extends Screen {
    private static final int BIT_MASK = 0xFF;

    private ExecutorService executorService = createExecutor();

    private static final Object2IntMap<ResourceKey<Biome>> COLORS = new Object2IntOpenHashMap<>();


    private final Long2ObjectLinkedOpenHashMap<CompletableFuture<Tile>> trackedTileFutures = new Long2ObjectLinkedOpenHashMap<>();

    private final ServerLevel level;

    private final BlockPos.MutableBlockPos origin;

    private int scrollCooldown;

    private final LongSet submitted = new LongOpenHashSet();
    float scale = 0.5F;

    private int shift = 9;


    int tileSize = tileToBlock(1);

    // Concurrent to avoid locking the main thread and will be replaced with batching later on.
    private final Set<Tile> toRender = ConcurrentHashMap.newKeySet();

    private BoundingBox worldViewArea;


    private int sampleResolution = 32;

    public LongList tilesToSubmit = new LongArrayList();

    public int submittedTaskCoolDown = 0;

    private boolean structuresNeedUpdates;


    // Wip. And New
    private final WorldScreenStructureSprites sprite = new WorldScreenStructureSprites();
    private final WorldScreenThreadSafety threadSafety = new WorldScreenThreadSafety();

    private final StringBuilder builder = new StringBuilder();

    private final Formatter formatter = new Formatter(builder);

    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f modelView = new Matrix4f();

    private final Object2IntMap<Holder<Biome>> biomeColors;
    private final Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, StructureRender> structureRendering = new Object2ObjectOpenHashMap<>();

    private final Tile.TileRenderType tileRenderType = Tile.TileRenderType.BIOME_HEIGHTMAP;

    private boolean heightMap = Tile.TileRenderType.BIOME_HEIGHTMAP == tileRenderType || Tile.TileRenderType.HEIGHTMAP == tileRenderType;

    private final boolean renderSlimeChunks = false;

    private int totalTilesToRender;

    public WorldScreen(Component $$0) {
        super($$0);
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Level.OVERWORLD);

        BlockPos playerBlockPos = Minecraft.getInstance().player.blockPosition();
        origin = new BlockPos.MutableBlockPos().set(playerBlockPos).setY(Mth.clamp(playerBlockPos.getY(), this.level.getMinBuildHeight(), this.level.getMaxBuildHeight()));
        setWorldArea();
        this.structuresNeedUpdates = true;

        // Colors.
        var map = new Object2IntOpenHashMap<Holder<Biome>>();

        var random = level.random;


        level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().forEach(holder -> {


            Biome value = holder.value();

            float baseTemperature = value.getBaseTemperature();
            float downfall = value.getDownfall();

            float lerp = Mth.inverseLerp(baseTemperature, -2, 2);
            int r = (int) Mth.clampedLerp(137, 139, lerp);
            int g = (int) Mth.clampedLerp(207, 0, lerp);
            int b = (int) Mth.clampedLerp(240, 0, lerp);

            int randomColor = FastColor.ARGB32.color(255, r, g, b);
            map.put(holder, BiomeLayer.COLORS.getOrDefault(holder.unwrapKey().orElseThrow(), randomColor));
        });

        computeStructureRenderers();

        biomeColors = Object2IntMaps.unmodifiable(map);
    }

    private void computeStructureRenderers() {
        var random = level.random;
        level.getChunkSource().getGenerator().possibleStructureSets().map(Holder::value).map(StructureSet::structures).forEach(structureSelectionEntries -> {
            for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSelectionEntries) {
                Holder<ConfiguredStructureFeature<?, ?>> structure = structureSelectionEntry.structure();
                var r = Mth.randomBetweenInclusive(random, 200, 256);
                var g = Mth.randomBetweenInclusive(random, 200, 256);
                var b = Mth.randomBetweenInclusive(random, 200, 256);
                int color = FastColor.ARGB32.color(255, r, g, b);

                ResourceLocation location = structure.unwrapKey().orElseThrow().location();

                if (!structureRendering.containsKey(structure)) {
                    StructureRender structureRender;
                    ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                    ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), "worldview/icon/structure/" + location.getPath() + ".png");

                    if (resourceManager.hasResource(resourceLocation)) {
                        try (DynamicTexture texture = new DynamicTexture(NativeImage.read(resourceManager.getResource(resourceLocation).getInputStream()))) {

                            structureRender = (stack, drawX, drawZ) -> {
                                RenderSystem.setShaderTexture(0, texture.getId());
                                RenderSystem.enableBlend();
                                var pixels = texture.getPixels();
//                                if (pixels == null) {
//                                    return;
//                                }
                                int width = (int) (pixels.getWidth() / scale);
                                int height = (int) (pixels.getHeight() / scale);
                                GuiComponent.blit(stack, drawX - (width / 2), drawZ - (height / 2), 0.0F, 0.0F, width, height, width, height);
                                RenderSystem.disableBlend();
                            };

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }


                    } else {
                        structureRender = (stack, drawX, drawZ) -> {
                            int range = 16;
//                            GuiComponent.fill(stack, drawX - range, drawZ - range, drawX + range, drawZ + range, color);
                        };
                    }

                    this.structureRendering.put(structure, structureRender);
                }
            }
        });
    }


    private void setWorldArea() {
        int xRange = getXTileRange();
        int zRange = getZTileRange();
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

        this.totalTilesToRender = xRange * zRange;
    }

    @Override
    public void tick() {
        this.scrollCooldown--;
        if (scrollCooldown < 0) {
            handleTileTracking();
        } else {
        }

        int currentlyRenderingCount = toRender.size();
//        if (currentlyRenderingCount >= this.totalTilesToRender) {
//            throw new RuntimeException(String.format("Too many tiles were rendering. Rendering %s when the total should not exceed %s.", currentlyRenderingCount, totalTilesToRender));
//        }
        super.tick();
    }

    private void handleTileTracking() {
        long originTile = tileKey(this.origin);

        int xTileRange = getXTileRange();
        int zTileRange = getZTileRange();

        for (int x = -xTileRange; x <= xTileRange; x++) {
            for (int z = -zTileRange; z <= zTileRange; z++) {
                int worldTileX = getTileX(originTile) + x;
                int worldTileZ = getTileZ(originTile) + z;
                long worldTile = LongPackingUtil.tileKey(worldTileX, worldTileZ);
                int worldX = getWorldXFromTileKey(worldTileX);
                int worldZ = getWorldZFromTileKey(worldTileZ);
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
                this.trackedTileFutures.computeIfAbsent(tilePos, key -> threadSafety.createCompletableFuture(heightMap, origin.getY(), tileSize, sampleResolution, level, shift, key, biomeColors));
            }
            this.tilesToSubmit.removeElements(0, to);
        }

        Set<Long> toRemove = ConcurrentHashMap.newKeySet();
        trackedTileFutures.forEach((tilePos, future) -> {
            int worldX = getWorldXFromTileKey(tilePos);
            int worldZ = getWorldZFromTileKey(tilePos);
            if (this.worldViewArea.intersects(worldX, worldZ, worldX, worldZ)) {
                future.thenAcceptAsync(tile -> {
                    toRender.add(tile);
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
        renderTiles(stack, mouseX, mouseZ, partialTicks);
        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    private void renderTiles(PoseStack stack, int mouseX, int mouseY, float partialTicks) {
        stack.pushPose();
        stack.scale(scale, scale, 0);
        GuiComponent.fill(stack, 0, 0, (int) (width / scale), (int) (height / scale), FastColor.ARGB32.color(255, 0, 0, 0));
        int screenCenterX = getScreenCenterX();
        int screenCenterZ = getScreenCenterZ();

        int scaledMouseX = (int) (mouseX / scale);
        int scaledMouseY = (int) (mouseY / scale);

        builder.setLength(0);
        MutableComponent tooltip = new TextComponent(formatter.format("%s, %s, %s", getWorldXFromMouseX(mouseX), "???", getWorldZFromMouseY(mouseY)).toString());

        for (Tile tileToRender : this.toRender) {
            int localX = getLocalXFromWorldX(tileToRender.getWorldX());
            int localZ = getLocalZFromWorldZ(tileToRender.getWorldZ());

            int screenTileMinX = (screenCenterX + localX);
            int screenTileMinZ = (screenCenterZ + localZ);
            tileToRender.render(stack, screenTileMinX, screenTileMinZ, this.tileRenderType, this.renderSlimeChunks);

            if (tileToRender.isMouseIntersecting(scaledMouseX, scaledMouseY, screenTileMinX, screenTileMinZ)) {
                Tile.DataAtPosition dataAtPosition = tileToRender.getBiomeAtMousePosition(scaledMouseX, scaledMouseY, screenTileMinX, screenTileMinZ);

                builder.setLength(0);
                var pos = dataAtPosition.worldPos();
                tooltip = new TextComponent(formatter.format("%s, %s, %s | ", pos.getX(), pos.getY(), pos.getZ()).toString()).append(getTranslationComponent(dataAtPosition.biomeHolder(), builder, formatter));
            }

        }

        for (Tile tileToRender : this.toRender) {
            tileToRender.getPositionsForStructure().forEach(((configuredStructureFeatureHolder, longs) -> {
                for (long structureChunkPos : longs) {
                    int structureWorldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(structureChunkPos), 7);
                    int structureWorldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(structureChunkPos), 7);

                    if (worldViewArea.intersects(structureWorldX, structureWorldZ, structureWorldX, structureWorldZ)) {
                        int drawX = screenCenterX + getLocalXFromWorldX(structureWorldX);
                        int drawZ = screenCenterZ + getLocalZFromWorldZ(structureWorldZ);

                        this.structureRendering.get(configuredStructureFeatureHolder).render(stack, drawX, drawZ);
                    }
                }
            }));
        }

        stack.popPose();

        renderTooltip(stack, tooltip, mouseX, mouseY);

        projection.setIdentity();

        modelView.setIdentity();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

//        sprite.draw(projection, modelView);

        glDisable(GL_BLEND);
    }

    @Override
    public void onClose() {
        threadSafety.close();
        sprite.close();

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
//
//            if (delta > 0) {
//                int worldXFromMouseX = getWorldXFromMouseX(mouseX);
//                int worldZFromMouseY = getWorldZFromMouseY(mouseY);
//                this.origin.set(worldXFromMouseX, this.origin.getY(), worldZFromMouseY);
//            }
            this.scale = (float) Mth.clamp(this.scale + (delta * (this.scale * 0.5F)), 0.03, 1.5);


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

    public int getXTileRange() {
        return blockToTile(getScreenCenterX()) + 2;
    }

    public int getZTileRange() {
        return blockToTile(getScreenCenterZ()) + 2;
    }

    //TODO: Figure out why this is incorrect.
    public int getWorldXFromMouseX(double mouseX) {
        int screenCenterX = getScreenCenterX();
        int scaledMouseX = (int) Math.round(mouseX / scale);

        return this.origin.getX() + (screenCenterX - scaledMouseX);
    }

    //TODO: Figure out why this is incorrect.
    public int getWorldZFromMouseY(double mouseZ) {
        int screenCenterZ = getScreenCenterZ();
        int scaledMouseZ = (int) Math.round(mouseZ / scale);

        return this.origin.getZ() + (screenCenterZ - scaledMouseZ);
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

    private static int parseColor(String hex) {
        if (hex.isEmpty()) {
            return 0;
        }
        return (255 << 24) | (int) Long.parseLong(hex.replace("#", "").replace("0x", ""), 16);
    }

    @FunctionalInterface
    public interface StructureRender {

        void render(PoseStack stack, int scaledX, int scaledZ);
    }
}