package com.corgitaco.worldviewer.cleanup;

import com.corgitaco.worldviewer.client.WorldScreen;
import com.example.examplemod.util.LongPackingUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.io.IOException;

import static com.corgitaco.worldviewer.cleanup.util.ClientUtil.isKeyOrMouseButtonDown;
import static com.example.examplemod.util.LongPackingUtil.getTileX;
import static com.example.examplemod.util.LongPackingUtil.getTileZ;

public class WorldScreenv2 extends Screen {

    public final int shift = 9;

    public int sampleResolution = 16;

    int tileSize = tileToBlock(1);


    public final BlockPos.MutableBlockPos origin = new BlockPos.MutableBlockPos();

    float scale = 0.5F;
    public ServerLevel level;

    public BoundingBox worldViewArea;

    private int scrollCooldown;

    private final Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, WorldScreen.StructureRender> structureRendering = new Object2ObjectOpenHashMap<>();

    RenderTileManager renderTileManager;

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
                int color = FastColor.ARGB32.color(255, r, g, b);

                ResourceLocation location = structure.unwrapKey().orElseThrow().location();

                if (!structureRendering.containsKey(structure)) {
                    WorldScreen.StructureRender structureRender;
                    ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
                    ResourceLocation resourceLocation = new ResourceLocation(location.getNamespace(), "worldview/icon/structure/" + location.getPath() + ".png");

                    if (resourceManager.hasResource(resourceLocation)) {
                        try (DynamicTexture texture = new DynamicTexture(NativeImage.read(resourceManager.getResource(resourceLocation).getInputStream()))) {

                            structureRender = (stack, drawX, drawZ) -> {
                                RenderSystem.setShaderTexture(0, texture.getId());
                                RenderSystem.enableBlend();
                                var pixels = texture.getPixels();
                                if (pixels == null) {
                                    return;
                                }
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
                            GuiComponent.fill(stack, drawX - range, drawZ - range, drawX + range, drawZ + range, color);
                        };
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

        this.renderTileManager = new RenderTileManager(level, origin);
        super.init();
    }

    @Override
    public void tick() {
        if (this.scrollCooldown < 0) {
            this.renderTileManager.tick(this);
        }

        scrollCooldown--;
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
        GuiComponent.fill(stack, 0, 0, (int) (width / scale), (int) (height / scale), FastColor.ARGB32.color(255, 0, 0, 0));

        this.renderTileManager.render(stack, mouseX, mouseY, partialTicks, this);


        drawGrid(stack);


        stack.popPose();
        super.render(stack, mouseX, mouseY, partialTicks);
    }

    private void drawGrid(PoseStack stack) {
        int gridColor = FastColor.ARGB32.color(100, 255, 255, 255);
        long originTile = tileKey(this.origin);
        int lineWidth = (int) Math.ceil(0.3 / scale);

        int xTileRange = getXTileRange();
        int xIncrement = 1;

        for (int x = -xTileRange; x < xTileRange; x += xIncrement) {
            int linePos = getScreenCenterX() + getLocalXFromWorldX(tileToBlock(getTileX(originTile) + x));
            GuiComponent.fill(stack, linePos - lineWidth, 0, linePos + lineWidth, (int) (height / scale), gridColor);
        }


        int zTileRange = getZTileRange();
        int increment = 1;
        for (int z = -zTileRange; z < zTileRange; z += increment) {
            int linePos = getScreenCenterZ() + getLocalZFromWorldZ(tileToBlock(getTileZ(originTile) + z));
            GuiComponent.fill(stack, 0, linePos - lineWidth, (int) (width / scale), linePos + lineWidth, gridColor);
        }

        renderCoordinates(stack, originTile, xTileRange, zTileRange);
    }

    private void renderCoordinates(PoseStack stack, long originTile, int xTileRange, int zTileRange) {
        for (int x = -xTileRange; x < xTileRange; x++) {
            for (int z = -zTileRange; z < zTileRange; z++) {
                int worldX = tileToBlock(getTileX(originTile) + x);
                int worldZ = tileToBlock(getTileZ(originTile) + z);

                int xScreenPos = getScreenCenterX() + getLocalXFromWorldX(worldX);
                int zScreenPos = getScreenCenterZ() + getLocalZFromWorldZ(worldZ);

                String formatted = "x%s,z%s".formatted(worldX, worldZ);
                MutableComponent component = new TextComponent(formatted).withStyle(ChatFormatting.BOLD);

                int textWidth = Minecraft.getInstance().font.width(component);
                float scale = Math.min((1F / this.scale) * 0.6F, 4);

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

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        this.origin.move((int) (dragX / scale), 0, (int) (dragY / scale));
        cull();
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isKeyOrMouseButtonDown(this.minecraft, this.minecraft.options.keyShift)) {
            if (!this.level.isOutsideBuildHeight((int) (this.origin.getY() + delta))) {
                this.origin.move(0, (int) delta, 0);
            }
        } else {
            this.scale = (float) Mth.clamp(this.scale + (delta * (this.scale * 0.5F)), 0.03, 1.5);
            cull();
        }
        this.scrollCooldown = 30;
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

    public Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, WorldScreen.StructureRender> getStructureRendering() {
        return structureRendering;
    }
}
