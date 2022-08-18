package com.example.examplemod.client;

import com.example.examplemod.mixin.UtilAccess;
import com.example.examplemod.mixin.client.KeyMappingAccess;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class WorldScreen extends Screen {


    private ExecutorService executorService = UtilAccess.invokeMakeExecutor("world-viewer");


    List<CompletableFuture<List<Tile>>> tiles = new ArrayList<>();

    private final ServerLevel level;

    private BlockPos.MutableBlockPos origin;

    private int scrollCooldown;

    private LongSet submitted = new LongOpenHashSet();

    Object2IntOpenHashMap<Holder<Biome>> colorForBiome = new Object2IntOpenHashMap<>();
    float scale = 0.5F;
    int size = 16;

    private final List<Tile> toRender = new ArrayList<>();

    private BoundingBox worldArea;


    public WorldScreen(Component $$0) {
        super($$0);
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Level.OVERWORLD);

        for (Holder<Biome> possibleBiome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            colorForBiome.put(possibleBiome, FastColor.ARGB32.color(255, level.random.nextInt(256), level.random.nextInt(256), level.random.nextInt(256)));
        }

        BlockPos playerBlockPos = Minecraft.getInstance().player.blockPosition();
        origin = new BlockPos.MutableBlockPos().set(playerBlockPos).setY(Mth.clamp(playerBlockPos.getY(), this.level.getMinBuildHeight(), this.level.getMaxBuildHeight()));
        setWorldArea();
    }

    private void setWorldArea() {
        int screenCenterX = (int) ((this.width / 2) / scale);
        int screenCenterZ = (int) ((this.height / 2) / scale);

        int xRange = SectionPos.blockToSectionCoord(screenCenterX) + 1;
        int zRange = SectionPos.blockToSectionCoord(screenCenterZ) + 1;
        this.worldArea = new BoundingBox(
                this.origin.getX() - screenCenterX - SectionPos.sectionToBlockCoord(xRange),
                level.getMinBuildHeight(),
                this.origin.getZ() - screenCenterZ - SectionPos.sectionToBlockCoord(zRange),
                this.origin.getX() + screenCenterX + SectionPos.sectionToBlockCoord(xRange),
                level.getMaxBuildHeight(),
                this.origin.getZ() + screenCenterZ + SectionPos.sectionToBlockCoord(zRange)
        );
    }

    @Override
    public void tick() {
        this.scrollCooldown--;

        if (scrollCooldown < 0) {

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

            int tilesPerThreadCount = (int) (100 / scale);
            for (int i = 0; i < tilesToSubmit.size(); i += tilesPerThreadCount) {
                LongList longList = tilesToSubmit.subList(i, Math.min(tilesToSubmit.size(), i + tilesPerThreadCount));
                LongList tilesForFuture = new LongArrayList();
                tilesForFuture.addAll(longList);
                this.tiles.add(CompletableFuture.supplyAsync(
                        () -> {
                            ArrayList<Tile> tiles = new ArrayList<>();
                            tilesForFuture.forEach(key -> {
                                int worldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(key));
                                int worldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(key));
                                tiles.add(new Tile(this.origin.getY(), worldX, worldZ, size, this.level::getBiome, this.colorForBiome));
                            });
                            return tiles;
                        }, executorService)
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
        }
        super.tick();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseZ, float partialTicks) {
        long originChunk = ChunkPos.asLong(this.origin);

        stack.pushPose();
        stack.scale(scale, scale, 0);

        int screenCenterX = (int) ((this.width / 2) / scale);
        int screenCenterZ = (int) ((this.height / 2) / scale);


        int scaledMouseX = (int) (mouseX / scale);
        int scaledMouseZ = (int) (mouseZ / scale);

        int worldX = this.origin.getX() - (screenCenterX - scaledMouseX);
        int worldZ = this.origin.getZ() - (screenCenterZ - scaledMouseZ);
        MutableComponent toolTipFrom = new TextComponent(String.format("%s, %s, %s", worldX, this.origin.getY(), worldZ));


        for (Tile tileToRender : this.toRender) {
            int localX = ChunkPos.getX(originChunk) - SectionPos.blockToSectionCoord(tileToRender.getWorldX());
            int localZ = ChunkPos.getZ(originChunk) - SectionPos.blockToSectionCoord(tileToRender.getWorldZ());

            int screenTileMinX = (screenCenterX + localX * size);
            int screenTileMinZ = (screenCenterZ + localZ * size);
            tileToRender.render(stack, screenTileMinX, screenTileMinZ);

            if (tileToRender.isMouseIntersecting(scaledMouseX, scaledMouseZ, screenTileMinX, screenTileMinZ)) {
                toolTipFrom.append(" | ").append(getTranslationComponent(tileToRender.getBiomeAtMousePosition(scaledMouseX, scaledMouseZ, screenTileMinX, screenTileMinZ)));
            }
        }

        stack.popPose();

        renderTooltip(stack, toolTipFrom, mouseX, mouseZ);
        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    @Override
    public void onClose() {
        terminateAllFutures();
        super.onClose();
    }

    private void terminateAllFutures() {
        executorService.shutdown();

        while (!executorService.isShutdown()) {
        }
        executorService = UtilAccess.invokeMakeExecutor("world-viewer");
    }

    @NotNull
    private static Component getTranslationComponent(Holder<Biome> biomeHolder) {
        ResourceLocation location = biomeHolder.unwrapKey().orElseThrow().location();
        String string = "biome." + location.getNamespace() + "." + location.getPath();

        Component hoverText;
        if (Language.getInstance().has(string)) {
            hoverText = new TranslatableComponent(string);
        } else {
            hoverText = new TextComponent(location.toString());
        }
        return hoverText;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.origin.move((int) (dragX / scale), 0, (int) (dragY / scale));
        setWorldArea();

        List<Tile> render = this.toRender;
        for (int i = 0; i < render.size(); i++) {
            Tile tileToRender = render.get(i);
            int worldX = tileToRender.getWorldX();
            int worldZ = tileToRender.getWorldZ();
            if (!this.worldArea.intersects(worldX, worldZ, worldX, worldZ)) {
                this.toRender.remove(i);
                this.submitted.remove(ChunkPos.asLong(SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ)));
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isKeyOrMouseButtonDown(this.minecraft, this.minecraft.options.keyShift)) {
            if (!this.level.isOutsideBuildHeight((int) (this.origin.getY() + delta))) {
                this.origin.move(0, (int) delta, 0);
                this.submitted.clear();
                terminateAllFutures();
                this.toRender.clear();
            }
        } else {
            this.scale = (float) Mth.clamp(this.scale + (delta * 0.05), 0.2, 1.5);
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
}