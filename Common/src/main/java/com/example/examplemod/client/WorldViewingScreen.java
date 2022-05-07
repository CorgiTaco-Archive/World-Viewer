package com.example.examplemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;

import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

public class WorldViewingScreen extends Screen {
    private static ExecutorService EXECUTOR_SERVICE = new ForkJoinPool(2); //TODO: Find a better way / time to create this
    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
    private final BlockPos playerOrigin = Minecraft.getInstance().player.blockPosition();
    private static final Tile BLANK_TILE = new Tile(1, 1, 0, 1, pos -> new DataAtPosition(FastColor.ARGB32.color(255, 0, 0, 0), new TextComponent("???")));

    ServerLevel level = server.getLevel(Level.OVERWORLD);

    Object2IntOpenHashMap<Holder<Biome>> colorForBiome = new Object2IntOpenHashMap<>();


    Long2ObjectLinkedOpenHashMap<CompletableFuture<Tile>> tiles = new Long2ObjectLinkedOpenHashMap<>();

    public DataAtPosition[][] colorAtCoord = null;

    public WorldViewingScreen(Component $$0) {
        super($$0);
        for (Holder<Biome> possibleBiome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
            colorForBiome.put(possibleBiome, FastColor.ARGB32.color(255, level.random.nextInt(256), level.random.nextInt(256), level.random.nextInt(256)));
        }
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
    private boolean readHeightmap = false;
    private int scrollCoolDown;
    private boolean showYToolTip;
    private Runnable updateCenter = () -> {
    };

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
                        new Tile(4, finalX, y, finalZ, blockPos -> {
                            if (readHeightmap) {
                                int baseHeight = level.getChunkSource().getGenerator().getBaseHeight(blockPos.getX(), blockPos.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, level.getLevel());
                                Holder<Biome> biomeHolder = level.getBiome(new BlockPos(blockPos.getX(), baseHeight, blockPos.getZ()));
                                return new DataAtPosition(this.colorForBiome.getInt(biomeHolder), getKey(biomeHolder.unwrapKey().orElseThrow().location()));
                            } else {
                                Holder<Biome> biomeHolder = level.getBiome(blockPos);
                                return new DataAtPosition(this.colorForBiome.getInt(biomeHolder), getKey(biomeHolder.unwrapKey().orElseThrow().location()));
                            }
                        }), EXECUTOR_SERVICE)
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
        if (scrollCoolDown >= 0) {
            scrollCoolDown--;
            if (scrollCoolDown == 0) {
                stopAndEmptyTiles();
                this.updateCenter.run();
                this.updateCenter = () -> {
                };
                this.showYToolTip = false;
            }
        }
        super.tick();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseZ, double pDelta) {
        Vector4f vector4f = new Vector4f(this.width / 2.0F, 0, this.height / 2.0F, 1);
        vector4f.transform(matrix4f);
        this.y += pDelta * -1;
        this.tiles.clear();
        this.updateCenter = () -> updateCenter((int) vector4f.x(), (int) vector4f.z(), scale);
        this.showYToolTip = true;
        this.scrollCoolDown = 20;
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
                int tileMinRenderX = tileToBlock(tileX);
                int tileMinRenderZ = tileToBlock(tileZ);
                long tileKey = tileKey(tileX, tileZ);
                if (tiles.containsKey(tileKey)) {
                    CompletableFuture<Tile> tileCompletableFuture = tiles.get(tileKey);
                    Tile tileFuture = tileCompletableFuture.getNow(null);

                    Objects.requireNonNullElse(tileFuture, BLANK_TILE).render(stack, tileMinRenderX, tileMinRenderZ);
                } else {
                    BLANK_TILE.render(stack, tileMinRenderX, tileMinRenderZ);
                }
            }
        }
        stack.popPose();

        Vector4f vector4f = new Vector4f(this.playerOrigin.getX(), this.playerOrigin.getY(), this.playerOrigin.getZ(), 1);
        vector4f.transform(this.matrix4fInverse);

        int screenPlayerX = Mth.floor(vector4f.x()) + this.width / 2;
        int screenPlayerZ = Mth.floor(vector4f.z()) + this.height / 2;

        fill(stack, screenPlayerX - 5, screenPlayerZ - 5, screenPlayerX + 5, screenPlayerZ + 5, FastColor.ARGB32.color(255, 255, 0, 255));

        if (this.showYToolTip && !readHeightmap) {
            renderTooltip(stack, new TextComponent(String.format("y= %s", this.y)), mouseX, mouseZ);
        } else {
            renderPositionTooltip(stack, mouseX, mouseZ);
        }

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
            Tile tile = Objects.requireNonNullElse(tileCompletableFuture.getNow(null), BLANK_TILE);
            DataAtPosition dataAtMousePosition = tile.dataAtPositions[localXTile][localZTile];
            MutableComponent component = new TextComponent(String.format("x=%s, y=%s, z=%s", flooredX, y, flooredZ)).append(" | ").append(dataAtMousePosition.displayName);
            renderTooltip(stack, component, mouseX, mouseZ);
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

    public static int tileToChunk(int tileCoord) {
        return SectionPos.blockToSectionCoord(tileToBlock(tileCoord));
    }

    public static int chunkToTile(int chunkCoord) {
        return blockToTile(SectionPos.sectionToBlockCoord(chunkCoord));
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
        while (this.tiles.size() > 0) {
            CompletableFuture<Tile> tileCompletableFuture = this.tiles.removeFirst();
            Tile tile = tileCompletableFuture.getNow(null);
            if (tile != null) {
                tile.texture.close();
            }
            tileCompletableFuture.cancel(true);
        }
    }

    public static void invertMatrix(Matrix4f matrix4f) {
        float det = matrix4f.adjugateAndDet();
        matrix4f.multiply(1 / det);
    }
}
