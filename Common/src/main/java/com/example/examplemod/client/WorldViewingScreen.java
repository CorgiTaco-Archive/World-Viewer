package com.example.examplemod.client;

import com.example.examplemod.mixin.UtilAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class WorldViewingScreen extends Screen {
    private static final ExecutorService EXECUTOR_SERVICE = UtilAccess.invokeMakeExecutor("world_viewer"); //TODO: Find a better way / time to create this
    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
    private final BlockPos playerOrigin = Minecraft.getInstance().player.blockPosition();

    ServerLevel level = server.getLevel(Level.OVERWORLD);

    Object2IntOpenHashMap<Holder<Biome>> colorForBiome = new Object2IntOpenHashMap<>();


    Long2ObjectLinkedOpenHashMap<CompletableFuture<Tile>> tiles = new Long2ObjectLinkedOpenHashMap<>();

    public DataAtPosition[][] colorAtCoord = null;

    public WorldViewingScreen(Component $$0) {
        super($$0);
        for (Holder<Biome> possibleBiome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            colorForBiome.put(possibleBiome, FastColor.ARGB32.color(255, 255, 255, 255) /*FastColor.ARGB32.color(255, level.random.nextInt(256), level.random.nextInt(256), level.random.nextInt(256))*/);
        }
        Registry<Biome> biomeRegistry = level.registryAccess().ownedRegistry(Registry.BIOME_REGISTRY).orElseThrow();
        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.FOREST), FastColor.ARGB32.color(255, 0, 255, 0));
        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.SNOWY_BEACH), FastColor.ARGB32.color(255, 255, 0, 0));
    }

    private int middleWidth;
    private int middleHeight;
    private int onScreenWorldMinX;
    private int onScreenWorldMaxX;
    private int onScreenWorldMinZ;
    private int onScreenWorldMaxZ;
    private int screenLengthX;
    private int screenLengthZ;
    private int onScreenWorldMinTileX;
    private int onScreenWorldMaxTileX;
    private int onScreenWorldMinTileZ;
    private int onScreenWorldMaxTileZ;
    private int screenTileWidth;
    private int screenTileHeight;
    private int playerX;
    private int playerZ;

    @Override
    protected void init() {
        colorAtCoord = new DataAtPosition[this.width + 1][this.height + 1];
        updateCenter(playerOrigin.getX(), playerOrigin.getZ());

        super.init();
    }

    private void updateCenter(int xOrigin, int zOrigin) {
        middleWidth = this.width - (this.width / 2);
        middleHeight = this.height - (this.height / 2);
        this.onScreenWorldMinX = xOrigin - middleWidth;
        this.onScreenWorldMaxX = xOrigin + middleWidth;
        this.onScreenWorldMinZ = zOrigin - middleHeight;
        this.onScreenWorldMaxZ = zOrigin + middleHeight;
        this.screenLengthX = onScreenWorldMaxX - onScreenWorldMinX;
        this.screenLengthZ = onScreenWorldMaxZ - onScreenWorldMinZ;

        this.playerX = Math.abs(onScreenWorldMaxX - playerOrigin.getX());
        this.playerZ = Math.abs(onScreenWorldMaxZ - playerOrigin.getZ());

        onScreenWorldMinTileX = blockToTile(onScreenWorldMinX);
        onScreenWorldMaxTileX = blockToTile(onScreenWorldMaxX);
        onScreenWorldMinTileZ = blockToTile(onScreenWorldMinZ);
        onScreenWorldMaxTileZ = blockToTile(onScreenWorldMaxZ);

        screenTileWidth = blockToTile(width);
        screenTileHeight = blockToTile(height);

        for (int screenTileX = 0; screenTileX <= screenTileWidth; screenTileX++) {
            for (int screenTileZ = 0; screenTileZ <= screenTileHeight; screenTileZ++) {
                int worldTileX = onScreenWorldMinTileX + screenTileX;
                int worldTileZ = onScreenWorldMinTileZ + screenTileZ;
                long tileKey = tileKey(worldTileX, worldTileZ);
                tiles.computeIfAbsent(tileKey, key ->
                    CompletableFuture.supplyAsync(() ->
                        new Tile(this.colorForBiome, worldTileX, worldTileZ, blockPos -> level.getBiome(blockPos)), EXECUTOR_SERVICE)
                );
            }
        }
    }

    @NotNull
    protected static Component getKey(ResourceLocation location) {
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
    public void tick() {
        super.tick();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseZ, float partialTicks) {
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.disableTexture();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int screenTileX = 0; screenTileX <= screenTileWidth; screenTileX++) {
            for (int screenTileZ = 0; screenTileZ <= screenTileHeight; screenTileZ++) {
                int worldTileX = onScreenWorldMinTileX + screenTileX;
                int worldTileZ = onScreenWorldMinTileZ + screenTileZ;
                long tileKey = tileKey(worldTileX, worldTileZ);
                if (tiles.containsKey(tileKey)) {
                    CompletableFuture<Tile> tileCompletableFuture = tiles.get(tileKey);
                    Tile tileFuture = tileCompletableFuture.getNow(null);
                    if (tileFuture != null) {
                        int tileMinRenderX = tileToBlock(screenTileX);
                        int tileMinRenderZ = tileToBlock(screenTileZ);
                        tileFuture.render(stack, bufferbuilder, tileMinRenderX, tileMinRenderZ);
                    } else {
                        fill(stack, tileToBlock(screenTileX) - 1, tileToBlock(screenTileZ) - 1, tileToMaxBlock(screenTileX), tileToMaxBlock(screenTileZ), FastColor.ARGB32.color(255, 0, 0, 0), bufferbuilder);
                    }
                }
            }
        }
        bufferbuilder.end();
        BufferUploader.end(bufferbuilder);
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();


        int playerColor = FastColor.ARGB32.color(255, 255, 0, 255);
        int playerScreenX = this.playerX;
        int playerScreenZ = this.playerZ;
        fill(stack, playerScreenX - 5, playerScreenZ - 5, playerScreenX + 5, playerScreenZ + 5, playerColor);
        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseZ, int button) {
        if (button == 0) {
            while (this.tiles.size() > 100) {
                this.tiles.removeFirst().cancel(true);
            }
            updateCenter((int) (this.onScreenWorldMinX + mouseX), (int) (this.onScreenWorldMinZ + mouseZ));
        }
        return super.mouseClicked(mouseX, mouseZ, button);
    }

    public static void fill(PoseStack pPoseStack, float pMinX, float pMinY, float pMaxX, float pMaxY, int pColor, BufferBuilder bufferBuilder) {
        innerFill(pPoseStack.last().pose(), pMinX, pMinY, pMaxX, pMaxY, pColor, bufferBuilder);
    }

    private static void innerFill(Matrix4f pMatrix, float pMinX, float pMinY, float pMaxX, float pMaxY, int pColor, BufferBuilder bufferbuilder) {
        if (pMinX < pMaxX) {
            float i = pMinX;
            pMinX = pMaxX;
            pMaxX = i;
        }

        if (pMinY < pMaxY) {
            float j = pMinY;
            pMinY = pMaxY;
            pMaxY = j;
        }

        float f3 = (float) (pColor >> 24 & 255) / 255.0F;
        float f = (float) (pColor >> 16 & 255) / 255.0F;
        float f1 = (float) (pColor >> 8 & 255) / 255.0F;
        float f2 = (float) (pColor & 255) / 255.0F;
        bufferbuilder.vertex(pMatrix, (float) pMinX, (float) pMaxY, 0.0F).color(f, f1, f2, f3).endVertex();
        bufferbuilder.vertex(pMatrix, (float) pMaxX, (float) pMaxY, 0.0F).color(f, f1, f2, f3).endVertex();
        bufferbuilder.vertex(pMatrix, (float) pMaxX, (float) pMinY, 0.0F).color(f, f1, f2, f3).endVertex();
        bufferbuilder.vertex(pMatrix, (float) pMinX, (float) pMinY, 0.0F).color(f, f1, f2, f3).endVertex();
    }

    public static boolean isInside(int minX, int minY, int maxX, int maxY, int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public static int getX(long regionPos) {
        return (int) (regionPos & 4294967295L);
    }

    public static int getZ(long regionPos) {
        return (int) (regionPos >>> 32 & 4294967295L);
    }

    // Region size is 256 x 256 chunks or 4096 x 4096 blocks
    public static long tileKey(int tileX, int tileZ) {
        return (long) tileX & 4294967295L | ((long) tileZ & 4294967295L) << 32;
    }

    public static int blockToTile(int blockCoord) {
        return blockCoord >> 7;
    }

    public static int tileToBlock(int tileCoord) {
        return tileCoord << 7;
    }

    public static int tileToMaxBlock(int tileCoord) {
        return tileToBlock(tileCoord + 1);
    }

    record DataAtPosition(int color, Component displayName) {
    }
}
