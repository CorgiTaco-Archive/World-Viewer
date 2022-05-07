package com.example.examplemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class WorldViewingScreen extends Screen {
    private static ExecutorService EXECUTOR_SERVICE = new ForkJoinPool(); //TODO: Find a better way / time to create this
    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
    private final BlockPos playerOrigin = Minecraft.getInstance().player.blockPosition();

    ServerLevel level = server.getLevel(Level.OVERWORLD);

    Object2IntOpenHashMap<Holder<Biome>> colorForBiome = new Object2IntOpenHashMap<>();


    Long2ObjectLinkedOpenHashMap<CompletableFuture<Tile>> tiles = new Long2ObjectLinkedOpenHashMap<>();

    public DataAtPosition[][] colorAtCoord = null;

    public WorldViewingScreen(Component $$0) {
        super($$0);
        for (Holder<Biome> possibleBiome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            colorForBiome.put(possibleBiome, FastColor.ARGB32.color(255, level.random.nextInt(256), level.random.nextInt(256), level.random.nextInt(256)));
        }
//        Registry<Biome> biomeRegistry = level.registryAccess().ownedRegistry(Registry.BIOME_REGISTRY).orElseThrow();
////        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.FOREST), FastColor.ARGB32.color(255, 0, 255, 0));
//        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.ICE_SPIKES), FastColor.ARGB32.color(255, 0, 0, 255));
////        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.BEACH), FastColor.ARGB32.color(255, 255, 255, 255));
////        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.OCEAN), FastColor.ARGB32.color(255, 0, 0, 255));
////        colorForBiome.put(biomeRegistry.getHolderOrThrow(Biomes.PLAINS), FastColor.ARGB32.color(255, 255, 0, 255));
    }

    private int onScreenWorldMinX;
    private int onScreenWorldMaxX;
    private int onScreenWorldMinZ;
    private int onScreenWorldMaxZ;
    private int onScreenWorldMinTileX;
    private int onScreenWorldMinTileZ;
    private int screenTileWidth;
    private int screenTileHeight;
    private int playerX;
    private int playerZ;
    private int y = 63;
    private float scale = 1;
    private final Matrix4f matrix4f = new Matrix4f();
    private Matrix4f matrix4fInverse = new Matrix4f();

    @Override
    protected void init() {
        colorAtCoord = new DataAtPosition[this.width + 1][this.height + 1];
        updateCenter(playerOrigin.getX(), playerOrigin.getZ(), 1);
        super.init();
    }

    private void updateCenter(int worldX, int worldZ, float scale) {
        matrix4f.setIdentity();
        matrix4f.multiply(Matrix4f.createScaleMatrix(scale, 1, scale));

        matrix4f.translate(new Vector3f(worldX, 0, worldZ));
        matrix4fInverse = matrix4f.copy();
        invertMatrix(matrix4fInverse);
//        if (!matrix4fInverse.invert()) {
//            throw new IllegalArgumentException("Could not invert matrix");
//        }


        Vector4f vector4fEndWorldCoord = new Vector4f(width / 2.0F, 0, height / 2.0F, 1);

        vector4fEndWorldCoord.transform(matrix4f);

        Vector4f vector4fStartWorldCoord = new Vector4f(-width / 2.0F, 0, -height / 2.0F, 1);

        vector4fStartWorldCoord.transform(matrix4f);

        this.onScreenWorldMinX = Mth.floor(vector4fStartWorldCoord.x());
        this.onScreenWorldMinZ = Mth.floor(vector4fStartWorldCoord.z());

        this.onScreenWorldMaxX = Mth.ceil(vector4fEndWorldCoord.x());
        this.onScreenWorldMaxZ = Mth.ceil(vector4fEndWorldCoord.z());


        int minScreenTileX = blockToTile(onScreenWorldMinX);
        int minScreenTileZ = blockToTile(onScreenWorldMinZ);

        int maxScreenTileX = blockToTile(onScreenWorldMaxX) + 1;
        int maxScreenTileZ = blockToTile(onScreenWorldMaxZ) + 1;

        for (int x = minScreenTileX; x <= maxScreenTileX; x++) {
            for (int z = minScreenTileZ; z <= maxScreenTileZ; z++) {
                long tileKey = tileKey(x, z);
                int finalX = x;
                int finalZ = z;
                System.out.printf("%s, %s%n", finalX, finalZ);

                tiles.computeIfAbsent(tileKey, key ->
                    CompletableFuture.supplyAsync(() ->
                        new Tile(this.colorForBiome, finalX, y, finalZ, blockPos -> level.getBiome(blockPos)), EXECUTOR_SERVICE)
                );
            }
        }
        System.out.printf("Scale %s size %s%n", scale, tiles.size());

        this.onScreenWorldMinTileX = minScreenTileX;
        this.onScreenWorldMinTileZ = minScreenTileZ;

        this.playerX = playerOrigin.getX() - tileToBlock(onScreenWorldMinTileX);
        this.playerZ = playerOrigin.getZ() - tileToBlock(onScreenWorldMinTileZ);

        this.screenTileWidth = maxScreenTileX - minScreenTileX + 1;
        this.screenTileHeight = maxScreenTileZ - minScreenTileZ + 1;
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
    public boolean mouseScrolled(double mouseX, double mouseZ, double pDelta) {
        Vector4f vector4f = new Vector4f((float) mouseX - width / 2.0F, 0, (float) mouseZ - height / 2.0F, 1);
        vector4f.transform(matrix4f);
        this.y += pDelta * -1;
        this.tiles.clear();
        stopAndEmptyTiles();
        updateCenter((int) vector4f.x(), (int) vector4f.z(), scale);
        return super.mouseScrolled(mouseX, mouseZ, pDelta);
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseZ, float partialTicks) {
        stack.pushPose();
//        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        Matrix4f swapCoordinates = new Matrix4f();
        swapCoordinates.load(FloatBuffer.wrap(new float[]
            {
                1, 0, 0, 0,
                0, 0, 1, 0,
                0, 1, 0, 0,
                0, 0, 0, 1
            }
        ));
        Matrix4f translateMatrix = Matrix4f.createTranslateMatrix(this.width / 2F, this.height / 2F, 0);

        Matrix4f worldToScreen = swapCoordinates.copy();
        worldToScreen.multiply(matrix4fInverse);
        worldToScreen.multiply(swapCoordinates);

        worldToScreen.multiply(translateMatrix);

        stack.mulPoseMatrix(worldToScreen);
        for (int x = 0; x <= this.screenTileWidth; x++) {
            for (int z = 0; z <= this.screenTileHeight; z++) {
                int tileX = this.onScreenWorldMinTileX + x;
                int tileZ = this.onScreenWorldMinTileZ + z;
                long tileKey = tileKey(tileX, tileZ);
                if (tiles.containsKey(tileKey)) {
                    CompletableFuture<Tile> tileCompletableFuture = tiles.get(tileKey);
                    Tile tileFuture = tileCompletableFuture.getNow(null);
                    if (tileFuture != null) {
                        int tileMinRenderX = tileToBlock(tileX);
                        int tileMinRenderZ = tileToBlock(tileZ);
                        tileFuture.render(stack, tileMinRenderX, tileMinRenderZ);
                    } else {
//                        fill(stack, tileToBlock(screenTileX) - 1, tileToBlock(screenTileZ) - 1, tileToMaxBlock(screenTileX), tileToMaxBlock(screenTileZ), FastColor.ARGB32.color(255, 0, 0, 0), bufferbuilder);
                    }
                }
            }
        }
        stack.popPose();

        Vector4f vector4f = new Vector4f(this.playerOrigin.getX(), this.playerOrigin.getY(), this.playerOrigin.getZ(), 1);
        vector4f.transform(this.matrix4fInverse);

        int screenPlayerX = Mth.floor(vector4f.x()) + this.width / 2;
        int screenPlayerZ = Mth.floor(vector4f.z()) + this.height / 2;

        fill(stack, screenPlayerX - 5, screenPlayerZ - 5, screenPlayerX + 5, screenPlayerZ + 5, FastColor.ARGB32.color(255, 255, 0, 255));

        renderPositionTooltip(stack, mouseX, mouseZ);


        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    private void renderPositionTooltip(PoseStack stack, int mouseX, int mouseZ) {
        Vector4f vector4f = new Vector4f(mouseX - width / 2.0f, 0, mouseZ - height / 2.0f, 1);
        vector4f.transform(this.matrix4f);

        int flooredX = Mth.floor(vector4f.x());
        int flooredZ = Mth.floor(vector4f.z());

        int mouseXTile = blockToTile(flooredX);
        int mouseZTile = blockToTile(flooredZ);

        int localXTile = flooredX - tileToBlock(mouseXTile);
        int localZTile = flooredZ - tileToBlock(mouseZTile);

        long worldTileKey = tileKey(mouseXTile, mouseZTile);

        if (tiles.containsKey(worldTileKey)) {
            CompletableFuture<Tile> tileCompletableFuture = tiles.get(worldTileKey);
            Tile tileFuture = tileCompletableFuture.getNow(null);
            if (tileFuture != null) {
                DataAtPosition dataAtMousePosition = tileFuture.dataAtPositions[localXTile][localZTile];
                MutableComponent component = new TextComponent(String.format("x=%s, y=%s, z=%s", flooredX, y, flooredZ)).append(" | ").append(dataAtMousePosition.displayName);
                renderTooltip(stack, component, mouseX, mouseZ);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseZ, int button) {
        Vector4f vector4f = new Vector4f((float) mouseX - width / 2.0F, 0, (float) mouseZ - height / 2.0F, 1);
        vector4f.transform(matrix4f);
        if (button == 0) {
            updateCenter((int) vector4f.x(), (int) vector4f.z(), scale);
        } else {
            updateCenter((int) vector4f.x(), (int) vector4f.z(), scale *= 2);
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
        return blockCoord >> 6;
    }

    public static int tileToBlock(int tileCoord) {
        return tileCoord << 6;
    }

    public static int tileToMaxBlock(int tileCoord) {
        return tileToBlock(tileCoord + 1);
    }

    record DataAtPosition(int color, Component displayName) {
    }

    @Override
    public void onClose() {
        stopAndEmptyTiles();
        super.onClose();
    }

    private void stopAndEmptyTiles() {
        EXECUTOR_SERVICE.shutdownNow();
//        while (this.tiles.size() > 0) {
//            CompletableFuture<Tile> tileCompletableFuture = this.tiles.removeFirst();
//            Tile tile = tileCompletableFuture.getNow(null);
//            if (tile != null) {
//                tile.texture.close();
//            }
//            tileCompletableFuture.cancel(true);
//        }
        this.tiles.clear();
        EXECUTOR_SERVICE = new ForkJoinPool();
    }

    public static void invertMatrix(Matrix4f matrix4f) {
        float det = matrix4f.adjugateAndDet();
        matrix4f.multiply(1 / det);
    }
}
