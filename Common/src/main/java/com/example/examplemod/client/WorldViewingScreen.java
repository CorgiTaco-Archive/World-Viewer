package com.example.examplemod.client;

import com.example.examplemod.mixin.UtilAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class WorldViewingScreen extends Screen {
    private static final ExecutorService EXECUTOR_SERVICE = UtilAccess.invokeMakeExecutor("world_viewer"); //TODO: Find a better way / time to create this
    MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
    BlockPos playerOrigin = Minecraft.getInstance().player.blockPosition();

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

    private int screenMinX;
    private int screenMaxX;
    private int screenMinZ;
    private int screenMaxZ;
    private int minTileX;
    private int maxTileX;
    private int minTileZ;
    private int maxTileZ;
    private int tileWidth;
    private int tileHeight;


    @Override
    protected void init() {
        colorAtCoord = new DataAtPosition[this.width + 1][this.height + 1];
        updateCenter(playerOrigin.getX(), playerOrigin.getZ());

        super.init();
    }

    private void updateCenter(int xOrigin, int zOrigin) {
        int middleWidth = (this.width - (this.width / 2));
        int middleHeight = (this.height - (this.height / 2));
        this.screenMinX = xOrigin - middleWidth;
        this.screenMaxX = xOrigin + middleWidth;
        this.screenMinZ = zOrigin - middleHeight;
        this.screenMaxZ = zOrigin + middleHeight;

        minTileX = blockToTile(screenMinX);
        maxTileX = blockToTile(screenMaxX);
        minTileZ = blockToTile(screenMinZ);
        maxTileZ = blockToTile(screenMaxZ);

        tileWidth = blockToTile(width);
        tileHeight = blockToTile(height);

        for (int screenTileX = 0; screenTileX <= tileWidth; screenTileX++) {
            for (int screenTileZ = 0; screenTileZ <= tileHeight; screenTileZ++) {
                int tileX = minTileX + screenTileX;
                int tileZ = minTileZ + screenTileZ;
                long tileKey = tileKey(tileX, tileZ);
                tiles.computeIfAbsent(tileKey, key -> {
                    BoundingBox boundingBox = new BoundingBox(
                        tileToBlock(tileX) - 1, 0, tileToBlock(tileZ) - 1,
                        tileToMaxBlock(tileX), 0, tileToMaxBlock(tileZ)
                    );

                    return CompletableFuture.supplyAsync(() -> new Tile(this.colorForBiome, boundingBox, blockPos -> level.getBiome((BlockPos) blockPos)), EXECUTOR_SERVICE);
                });
            }
        }
    }

    @NotNull
    private static Component getKey(ResourceLocation location) {
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
        Component toolTip = null;
        for (int screenTileX = 0; screenTileX <= tileWidth; screenTileX++) {
            for (int screenTileZ = 0; screenTileZ <= tileHeight; screenTileZ++) {
                int tileX = minTileX + screenTileX;
                int tileZ = minTileZ + screenTileZ;
                long tileKey = tileKey(tileX, tileZ);
                if (tiles.containsKey(tileKey)) {
                    CompletableFuture<Tile> tileCompletableFuture = tiles.get(tileKey);
                    Tile tileFuture = tileCompletableFuture.getNow(null);
                    if (tileFuture != null) {
                        Optional<Component> optionalComponent = tileFuture.render(stack, bufferbuilder, tileToBlock(screenTileX) - 1, tileToBlock(screenTileZ) - 1, tileToMaxBlock(screenTileX), tileToMaxBlock(screenTileZ), mouseX, mouseZ);
                        if (optionalComponent.isPresent()) {
                            toolTip = optionalComponent.get();
                        }
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

        if (toolTip != null) {
            this.renderTooltip(stack, toolTip, mouseX, mouseZ);
        }


        int renderedPlayerX = screenMaxX - this.playerOrigin.getX();
        int renderPlayerZ = screenMaxZ - this.playerOrigin.getZ();

        hLine(stack, renderedPlayerX - 5, renderedPlayerX + 5, renderPlayerZ, FastColor.ARGB32.color(255, 0, 0, 0));
        vLine(stack, renderedPlayerX, renderPlayerZ - 5, renderPlayerZ + 5, FastColor.ARGB32.color(255, 0, 0, 0));

        super.render(stack, mouseX, mouseZ, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseZ, int button) {
        if (button == 0) {
            while (this.tiles.size() > 100) {
                this.tiles.removeFirst().cancel(true);
            }
            updateCenter((int) (this.screenMinX + mouseX), (int) (this.screenMinZ + mouseZ));
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


    record DataAtPosition(int color, Component displayName) {
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
        return tileToBlock(tileCoord + 1) - 1;
    }

    static class Tile {
        private final BoundingBox boundingBox;

        private final DataAtPosition[][] dataAtPositions;

        Tile(Object2IntOpenHashMap<Holder<Biome>> color, BoundingBox boundingBox, Function<BlockPos, Holder<Biome>> biomeGetter) {
            this.boundingBox = boundingBox;
            dataAtPositions = new DataAtPosition[boundingBox.getXSpan() + 1][boundingBox.getZSpan() + 1];
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < boundingBox.getXSpan(); x++) {
                for (int z = 0; z < boundingBox.getZSpan(); z++) {
                    mutableBlockPos.set(x + boundingBox.minX(), 63, z + boundingBox.minZ());
                    Holder<Biome> biomeHolder = biomeGetter.get(mutableBlockPos);
                    dataAtPositions[x][z] = new DataAtPosition(color.getInt(biomeHolder), getKey(biomeHolder.unwrapKey().orElseThrow().location()));
                }
            }
        }

        public Optional<Component> render(PoseStack stack, BufferBuilder bufferbuilder, int tileMinX, int tileMinZ, int tileMaxX, int tileMaxZ, int mouseX, int mouseZ) {
            for (int x = 0; x < this.boundingBox.getXSpan() - 1; x++) {
                for (int z = 0; z < this.boundingBox.getZSpan() - 1; z++) {
                    DataAtPosition dataAtPosition = this.dataAtPositions[x][z];
                    int fillMinX = tileMinX + x;
                    int fillMinZ = tileMinZ + z;
//                    if (!isInside(minX, minZ, maxX, maxZ, fillMinX, fillMinZ)) {
//                        continue;
//                    }


                    fill(stack, fillMinX, fillMinZ, fillMinX + 1, fillMinZ + 1, dataAtPosition.color(), bufferbuilder);
                }
            }


            if (isInside(tileMinX, tileMinZ, tileMaxX, tileMaxZ, mouseX, mouseZ)) {
                int positionDataX = mouseX - tileMinX;
                int positionDataZ = mouseZ - tileMinZ;
                MutableComponent toolTipComponent = new TextComponent(new BlockPos(this.boundingBox.minX() + mouseX, 63, this.boundingBox.minZ() + mouseZ).toString()).append(" | ").append(this.dataAtPositions[positionDataX][positionDataZ].displayName);
                return Optional.of(toolTipComponent);
            }
            return Optional.empty();
        }
    }
}
