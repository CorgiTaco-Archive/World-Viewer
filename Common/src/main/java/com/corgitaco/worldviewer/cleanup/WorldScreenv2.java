package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.corgitaco.worldviewer.cleanup.tile.RenderTile;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.BiomeLayer;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.corgitaco.worldviewer.client.screen.WidgetList;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.*;
import java.util.function.DoubleConsumer;

import static com.corgitaco.worldviewer.cleanup.util.ClientUtil.isKeyOrMouseButtonDown;
import static com.example.examplemod.util.LongPackingUtil.getTileX;
import static com.example.examplemod.util.LongPackingUtil.getTileZ;

public class WorldScreenv2 extends Screen {

    public int shift = 7;


    int tileSize = tileToBlock(1);

    public int sampleResolution = tileSize >> 6;

    public final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    float scale = 0.5F;
    public ServerLevel level;

    public BoundingBox worldViewArea;

    private int coolDown;

    private final Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, StructureRender> structureRendering = new Object2ObjectOpenHashMap<>();

    RenderTileManager renderTileManager;

    private WidgetList list;

    protected final Map<String, Float> opacities = new HashMap<>();

    private long lastClickTime;


    public WorldScreenv2(Component title) {
        super(title);
    }

    private void computeStructureRenderers() {
        var random = level.random;
        level.getChunkSource().getGenerator().possibleStructureSets().map(Holder::value).map(StructureSet::structures).forEach(structureSelectionEntries -> {
            for (StructureSet.StructureSelectionEntry structureSelectionEntry : structureSelectionEntries) {
                Holder<ConfiguredStructureFeature<?, ?>> structure = structureSelectionEntry.structure();
                var r = Mth.randomBetweenInclusive(random, 200, 256);
                var g = Mth.randomBetweenInclusive(random, 200, 256);
                var b = Mth.randomBetweenInclusive(random, 200, 256);

                ResourceLocation location = structure.unwrapKey().orElseThrow().location();

                if (!structureRendering.containsKey(structure)) {
                    StructureRender structureRender;
                    ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                    ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), "worldview/icon/structure/" + location.getPath() + ".png");

                    if (resourceManager.hasResource(resourceLocation)) {
                        try (DynamicTexture texture = new DynamicTexture(NativeImage.read(resourceManager.getResource(resourceLocation).getInputStream()))) {

                            structureRender = (stack, minDrawX, minDrawZ, maxDrawX, maxDrawZ, opacity) -> {
                                RenderSystem.setShaderTexture(0, texture.getId());
                                RenderSystem.enableBlend();
                                var pixels = texture.getPixels();
                                if (pixels == null) {
                                    return;
                                }

                                int drawX = (maxDrawX - minDrawX / 2);
                                int drawZ = (maxDrawZ - minDrawZ / 2);

                                int width = (int) (pixels.getWidth() / scale);
                                int height = (int) (pixels.getHeight() / scale);
                                GuiComponent.blit(stack, drawX - (width / 2), drawZ - (height / 2), 0.0F, 0.0F, width, height, width, height);
                                RenderSystem.disableBlend();
                            };

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }


                    } else {
                        structureRender = (stack, minDrawX, minDrawZ, maxDrawX, maxDrawZ, opacity) -> GuiComponent.fill(stack, minDrawX, minDrawZ, maxDrawX, maxDrawZ, FastColor.ARGB32.color((int) (255 * opacity), r, g, b));
                    }

                    this.structureRendering.put(structure, structureRender);
                }
            }
        });
    }


    @Override
    protected void init() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.level = server.getLevel(Minecraft.getInstance().level.dimension());
        this.origin.set(Minecraft.getInstance().player.blockPosition());
        computeStructureRenderers();
        setWorldArea();

        this.renderTileManager = new RenderTileManager(this, level, origin);

        int buttonWidth = 120;
        int buttonHeight = 20;

        List<AbstractWidget> opacity = new ArrayList<>();
        for (String key : TileLayer.FACTORY_REGISTRY.keySet()) {
            opacities.put(key, 1.0F);
            opacity.add(new Slider(0, 0, buttonWidth, buttonHeight, new TextComponent("%s opacity".formatted(key)), 1, value -> {
                opacities.put(key, (float) Mth.clamp(value, 0F, 1F));
            }));
        }

        int itemHeight = buttonHeight + 2;

        int bottomPos = this.height - 70;
        int listRenderedHeight = bottomPos + (buttonHeight * 3);

        this.list = new WidgetList(opacity, buttonWidth + 10, listRenderedHeight, bottomPos, listRenderedHeight + 10, itemHeight);

        this.list.setLeftPos(0);
        addRenderableWidget(this.list);
        super.init();
    }

    @Override
    public void tick() {
        if (this.coolDown == 0) {
            this.renderTileManager.blockGeneration = false;
        }
        this.renderTileManager.tick();

        coolDown--;
        super.tick();
    }

    @Override
    public void onClose() {
        this.renderTileManager.close();
        super.onClose();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float partialTicks) {

        stack.pushPose();
        stack.scale(scale, scale, 0);
        GuiComponent.fill(stack, 0, 0, (int) (width / scale), (int) (height / scale), FastColor.ARGB32.color(255, 255, 255, 255));

        this.renderTileManager.render(stack, mouseX, mouseY, partialTicks, this);

        drawGrid(stack);

        stack.popPose();

        if (!overWidget(mouseX, mouseY)) {
            renderToolTip(stack, mouseX, mouseY);
        }
        super.render(stack, mouseX, mouseY, partialTicks);
    }

    private void renderToolTip(PoseStack stack, int mouseX, int mouseY) {
        BlockPos mouseWorldPos = getMouseWorldPos(mouseX, mouseY);

        long mouseTileKey = tileKey(mouseWorldPos);
        RenderTile renderTile = this.renderTileManager.rendering.get(mouseTileKey);

        List<Component> toolTip = buildToolTip(mouseWorldPos, this.renderTileManager.getDataTileManager());
        if (renderTile != null) {
            int mouseTileLocalX = (mouseWorldPos.getX() - renderTile.getTileWorldX());
            int mouseTileLocalY = (mouseWorldPos.getZ() - renderTile.getTileWorldZ());
            toolTip.addAll(renderTile.toolTip(mouseX, mouseY, mouseWorldPos.getX(), mouseWorldPos.getZ(), mouseTileLocalX, mouseTileLocalY));
            toolTip.add(new TextComponent("Sample Resolution: %s blocks".formatted(renderTile.getSampleRes())));
            toolTip.add(new TextComponent("Tile size: %s blocks ".formatted(renderTile.getSize())));
        }

        renderTooltip(stack, toolTip, Optional.empty(), mouseX, mouseY);

    }

    @NotNull
    private Vec3 getMouseWorldVec3(double mouseX, double mouseY) {
        double scaledMouseX = mouseX / scale;
        double scaledMouseZ = mouseY / scale;

        double mouseWorldX = this.origin.getX() - (scaledMouseX - getScreenCenterX());
        double mouseWorldZ = this.origin.getZ() - (scaledMouseZ - getScreenCenterZ());

        return new Vec3(mouseWorldX, 0, mouseWorldZ);
    }

    private BlockPos getMouseWorldPos(double mouseX, double mouseY) {
        return new BlockPos(getMouseWorldVec3(mouseX, mouseY));
    }


    private static List<Component> buildToolTip(BlockPos mouseWorldPos, DataTileManager dataTileManager) {
        List<Component> components = new ArrayList<>();
        int mouseWorldX = mouseWorldPos.getX();
        int mouseWorldZ = mouseWorldPos.getZ();

        components.add(new TextComponent("x=%s, z=%s".formatted(mouseWorldX, mouseWorldZ)).withStyle(ChatFormatting.BOLD));

        ResourceKey<Biome> biomeResourceKey = dataTileManager.getBiomeRaw(mouseWorldX, mouseWorldZ).unwrapKey().orElseThrow();
        int styleColor = FastColor.ARGB32.multiply(FastColor.ARGB32.color(255, 255, 255, 255), BiomeLayer.FAST_COLORS.getInt(biomeResourceKey));
        components.add(new TextComponent("Biome: " + biomeResourceKey.location().toString()).setStyle(Style.EMPTY.withColor(styleColor)));

        boolean slimeChunkRaw = dataTileManager.isSlimeChunkRaw(SectionPos.blockToSectionCoord(mouseWorldX), SectionPos.blockToSectionCoord(mouseWorldZ));
        components.add(new TextComponent("Slime Chunk? %s".formatted(slimeChunkRaw ? "Yes" : "No")).setStyle(Style.EMPTY.withColor(slimeChunkRaw ? FastColor.ARGB32.color(124, 120, 190, 93) : FastColor.ARGB32.color(255, 255, 255, 255))));

        int oceanFloorHeight = dataTileManager.getHeightRaw(Heightmap.Types.OCEAN_FLOOR, mouseWorldX, mouseWorldZ);
        components.add(new TextComponent("Surface height = %s".formatted(oceanFloorHeight)));

        return components;
    }

    private void drawGrid(PoseStack stack) {
        int gridColor = FastColor.ARGB32.color(100, 255, 255, 255);
        long originTile = tileKey(this.origin);
        int lineWidth = (int) Math.ceil(0.75 / scale);

        int xTileRange = getXTileRange();
        int xIncrement = 1;

        int everyAmount = getXTileRange() / 2;

        for (int x = -xTileRange; x < xTileRange; x += xIncrement) {
            int tileX = getTileX(originTile) + x;
            if (tileX % everyAmount == 0) {

                int linePos = getScreenCenterX() + getLocalXFromWorldX(tileToBlock(tileX));
                GuiComponent.fill(stack, linePos - lineWidth, 0, linePos + lineWidth, (int) (height / scale), gridColor);
            }
        }


        int zTileRange = getZTileRange();
        int increment = 1;
        for (int z = -zTileRange; z < zTileRange; z += increment) {
            int tileZ = getTileZ(originTile) + z;
            if (tileZ % everyAmount == 0) {
                int linePos = getScreenCenterZ() + getLocalZFromWorldZ(tileToBlock(tileZ));
                GuiComponent.fill(stack, 0, linePos - lineWidth, (int) (width / scale), linePos + lineWidth, gridColor);
            }
        }

        renderCoordinates(stack, originTile, xTileRange, zTileRange, everyAmount);
    }

    private void renderCoordinates(PoseStack stack, long originTile, int xTileRange, int zTileRange, int everyAmount) {
        for (int x = -xTileRange; x < xTileRange; x++) {
            for (int z = -zTileRange; z < zTileRange; z++) {
                int tileX = getTileX(originTile) + x;
                int tileZ = getTileZ(originTile) + z;
                if (tileX % everyAmount == 0 && tileZ % everyAmount == 0) {


                    int worldX = tileToBlock(tileX);
                    int worldZ = tileToBlock(tileZ);

                    int xScreenPos = getScreenCenterX() + getLocalXFromWorldX(worldX);
                    int zScreenPos = getScreenCenterZ() + getLocalZFromWorldZ(worldZ);

                    String formatted = "x%s,z%s".formatted(worldX, worldZ);
                    MutableComponent component = new TextComponent(formatted).withStyle(ChatFormatting.BOLD);

                    int textWidth = Minecraft.getInstance().font.width(component);
                    float scale = (1F / this.scale) * 0.9F;

                    float fontRenderX = xScreenPos - ((textWidth / 2F) * scale);
                    float fontRenderZ = zScreenPos - (Minecraft.getInstance().font.lineHeight * scale);

                    stack.pushPose();
                    stack.translate(fontRenderX, fontRenderZ, 0);
                    stack.scale(scale, scale, scale);
                    Minecraft.getInstance().font.drawShadow(stack, component, 0, 0, FastColor.ARGB32.color(255, 255, 255, 255));
                    stack.popPose();
                }
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (!overWidget(mouseX, mouseY)) {
                this.origin.move((int) (dragX / scale), 0, (int) (dragY / scale));
                cull();
                this.coolDown = 10;
                this.renderTileManager.blockGeneration = true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseZ, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_2) {
            Vec3 mouseWorldVec = getMouseWorldVec3(mouseX, mouseZ);

            BlockPos mouseWorldPos = new BlockPos(mouseWorldVec);
            LocalPlayer localPlayer = this.minecraft.player;
            if (System.currentTimeMillis() - lastClickTime < 500) {
                UUID uuid = localPlayer.getUUID();
                localPlayer.displayClientMessage(new TextComponent("Preparing to teleport ").withStyle(ChatFormatting.YELLOW).append(localPlayer.getDisplayName()).append(" to ").append(new TextComponent("x=%.1f, z=%.1f".formatted(mouseWorldVec.x, mouseWorldVec.z)).withStyle(ChatFormatting.AQUA)), false);
                this.level.getServer().submit(() -> {
                    Vec3 tpPos = mouseWorldVec.add(0, this.level.getChunk(mouseWorldPos).getHeight(Heightmap.Types.WORLD_SURFACE, (int) mouseWorldVec.x, (int) mouseWorldVec.z) + 20, 0);
                    Player serverPlayer = this.level.getPlayerByUUID(uuid);
                    if (serverPlayer != null) {
                        serverPlayer.teleportTo(mouseWorldVec.x, tpPos.y, mouseWorldVec.z);
                        serverPlayer.displayClientMessage(new TextComponent("Teleported ").withStyle(ChatFormatting.GREEN).append(serverPlayer.getDisplayName()).append(" to ").append(new TextComponent("%.1f, %.1f, %.1f".formatted(mouseWorldVec.x, tpPos.y, mouseWorldVec.z)).withStyle(ChatFormatting.AQUA)), false);
                    }
                });

                this.origin.set(mouseWorldVec.x, 0, mouseWorldVec.z);
            }

            Minecraft.getInstance().keyboardHandler.setClipboard(String.format(Locale.ROOT, "/execute in %s run tp @s %.2f ~ %.2f %.2f %.2f", this.level.dimension().location(), mouseWorldVec.x, mouseWorldVec.z, localPlayer.getYRot(), localPlayer.getXRot()));

            lastClickTime = System.currentTimeMillis();
        }
        return super.mouseClicked(mouseX, mouseZ, button);
    }

    private boolean overWidget(double mouseX, double mouseY) {
        boolean overWidget = false;
        for (GuiEventListener child : this.children()) {
            if (child.isMouseOver(mouseX, mouseY)) {
                overWidget = true;
                break;
            }
        }
        return overWidget;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!overWidget(mouseX, mouseY)) {
            if (isKeyOrMouseButtonDown(this.minecraft, this.minecraft.options.keyShift)) {
                if (!this.level.isOutsideBuildHeight((int) (this.origin.getY() + delta))) {
                    this.origin.move(0, (int) delta, 0);
                }
            } else {
                int prevShift = shift;
                shift = (int) Mth.clamp(shift - delta, 6, 24);
                if (prevShift != shift) {
                    tileSize = tileToBlock(1);
                    sampleResolution = Math.max(1, tileSize >> 6);

                    if (delta > 0) {
                        this.scale = (float) (this.scale + (delta * (this.scale)));
                    } else {
                        this.scale = (float) (this.scale + (delta * (this.scale * 0.5)));

                    }
                    this.coolDown = 30;
                    this.renderTileManager.blockGeneration = true;
                    this.renderTileManager.onScroll();
                    cull();
                }
            }
        }
        return true;
    }

    private void cull() {
        setWorldArea();
        this.renderTileManager.cull(this);
    }


    private void setWorldArea() {
        int xRange = getXTileRange();
        int zRange = getZTileRange();
        this.worldViewArea = BoundingBox.fromCorners(
                new Vec3i(
                        Math.max(this.level.getWorldBorder().getMinX(), this.origin.getX() - tileToBlock(xRange) - 1),
                        level.getMinBuildHeight(),
                        Math.max(this.level.getWorldBorder().getMinZ(), this.origin.getZ() - tileToBlock(zRange) - 1)
                ),
                new Vec3i(
                        Math.min(this.level.getWorldBorder().getMaxX(), this.origin.getX() + tileToBlock(xRange) + 1),
                        level.getMaxBuildHeight(),
                        Math.min(this.level.getWorldBorder().getMaxZ(), this.origin.getZ() + tileToBlock(zRange) + 1)
                )
        );
    }

    public long tileKey(BlockPos pos) {
        return LongPackingUtil.tileKey(blockToTile(pos.getX()), blockToTile(pos.getZ()));
    }

    public int blockToTile(int blockCoord) {
        return blockToTile(blockCoord, this.shift);
    }

    public int blockToTile(int blockCoord, int shift) {
        return LongPackingUtil.blockToTile(blockCoord, shift);
    }


    public int tileToBlock(int tileCoord) {
        return tileToBlock(tileCoord, this.shift);
    }

    public int tileToBlock(int tileCoord, int shift) {
        return LongPackingUtil.tileToBlock(tileCoord, shift);
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
        return getTileX(getOriginTile()) - blockToTile(worldX);
    }

    public int getTileLocalZFromWorldZ(int worldZ) {
        return getTileZ(getOriginTile()) - blockToTile(worldZ);
    }

    public long getOriginTile() {
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

    //TODO: Figure out why this is incorrect.

    public Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, StructureRender> getStructureRendering() {
        return structureRendering;
    }


    @FunctionalInterface
    public interface StructureRender {

        void render(PoseStack stack, int minDrawX, int minDrawZ, int maxDrawX, int maxDrawZ, float opacity);
    }

    private static class Slider extends AbstractSliderButton {

        private final DoubleConsumer apply;

        public Slider(int x, int y, int width, int height, Component message, double value, DoubleConsumer apply) {
            super(x, y, width, height, message, value);
            this.apply = apply;
        }

        @Override
        protected void updateMessage() {

        }

        @Override
        protected void applyValue() {
            this.apply.accept(this.value);
        }
    }
}
