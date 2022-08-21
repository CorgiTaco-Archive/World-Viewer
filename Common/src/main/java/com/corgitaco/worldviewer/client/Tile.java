package com.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.*;

public final class Tile implements AutoCloseable {
    public final DynamicTexture texture;
    private final int worldX;
    private final int worldZ;
    private final int size;

    private final DataAtPosition[][] dataAtPos;

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = Collections.synchronizedMap(new HashMap<>());
    public final DynamicTexture slimeChunks;


    public Tile(boolean heightMap, int ySample, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level, Object2IntMap<Holder<Biome>> colorLookup) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;
        this.dataAtPos = new DataAtPosition[size][size];

        var generator = level.getChunkSource().getGenerator();

        NativeImage nativeImage = new NativeImage(size, size, true);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();

        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, ySample, worldZ - sampleZ);
                Holder<Biome> biomeHolder = level.getBiome(worldPos);
                int y = ySample;
                if (heightMap) {
                    boolean hasChunk = level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()));
                    if (hasChunk) {
                        y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());
                    } else {
                        y = generator.getBaseHeight(worldPos.getX(), worldPos.getZ(), Heightmap.Types.OCEAN_FLOOR, level);
                    }
                }

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;
                        dataAtPos[dataX][dataZ] = new DataAtPosition(biomeHolder, new BlockPos(worldPos.getX(), y, worldPos.getZ()));
                        nativeImage.setPixelRGBA(dataX, dataZ, colorLookup.getOrDefault(biomeHolder, 0));
                    }
                }
            }
        }


        NativeImage lazySlimeChunks = null;

        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(worldX) - x;
                int chunkZ = SectionPos.blockToSectionCoord(worldZ) - z;
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);

                boolean isSlimeChunk = WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, level.getSeed(), 987234911L).nextInt(10) == 0;

                if (isSlimeChunk) {
                    if (lazySlimeChunks == null) {
                        lazySlimeChunks = new NativeImage(size, size, true);
                        lazySlimeChunks.fillRect(0, 0, size, size, FastColor.ARGB32.color(0, 0, 0, 0));
                    }

                    int dataX = SectionPos.sectionToBlockCoord(x);
                    int dataZ = SectionPos.sectionToBlockCoord(z);

                    lazySlimeChunks.fillRect(dataX, dataZ, 16, 16, FastColor.ARGB32.color(255, 120, 190, 93));
                }


                for (Holder<StructureSet> structureSetHolder : level.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY).asHolderIdMap()) {

                    StructureSet structureSet = structureSetHolder.value();

                    if (structureSet.placement().isFeatureChunk(generator, level.getSeed(), chunkX, chunkZ)) {
                        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
                        worldgenrandom.setLargeFeatureSeed(level.getSeed(), chunkX, chunkZ);
                        List<StructureSet.StructureSelectionEntry> arraylist = new ArrayList<>(structureSet.structures());

                        int i = 0;

                        for (StructureSet.StructureSelectionEntry structureset$structureselectionentry1 : arraylist) {
                            i += structureset$structureselectionentry1.weight();
                        }

                        while (!arraylist.isEmpty()) {
                            int j = worldgenrandom.nextInt(i);
                            int k = 0;

                            for (StructureSet.StructureSelectionEntry structureset$structureselectionentry2 : arraylist) {
                                j -= structureset$structureselectionentry2.weight();
                                if (j < 0) {
                                    break;
                                }

                                ++k;
                            }

                            StructureSet.StructureSelectionEntry structureset$structureselectionentry3 = arraylist.get(k);
                            Holder<ConfiguredStructureFeature<?, ?>> configuredStructureFeatureHolder = structureset$structureselectionentry3.structure();
                            ConfiguredStructureFeature<?, ?> value = configuredStructureFeatureHolder.value();
                            if (canCreate(level, generator, chunkX, chunkZ, value)) {
                                this.positionsForStructure.computeIfAbsent(configuredStructureFeatureHolder, key -> new LongArraySet()).add(chunkKey);
                                break;
                            }

                            arraylist.remove(k);
                            i -= structureset$structureselectionentry3.weight();
                        }

                    }
                }
            }
        }
        texture = new DynamicTexture(nativeImage);

        if (lazySlimeChunks == null) {
            this.slimeChunks = null;
        } else {
            this.slimeChunks = new DynamicTexture(lazySlimeChunks);
        }
    }

    private static <FC extends FeatureConfiguration, F extends StructureFeature<FC>> boolean canCreate(ServerLevel level, ChunkGenerator generator, int x, int z, ConfiguredStructureFeature<FC, F> value) {
        return value.feature.canGenerate(level.registryAccess(), generator, generator.getBiomeSource(), level.getStructureManager(), level.getSeed(), new ChunkPos(x, z), value.config, level, value.biomes()::contains);
    }

    public Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> getPositionsForStructure() {
        return positionsForStructure;
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldZ() {
        return worldZ;
    }

    public boolean isMouseIntersecting(int scaledMouseX, int scaledMouseZ, int screenTileMinX, int screenTileMinZ) {
        boolean greaterThanMinX = scaledMouseX >= screenTileMinX;
        boolean greaterThanMinZ = scaledMouseZ >= screenTileMinZ;
        boolean lessThanMaxX = scaledMouseX < screenTileMinX + size;
        boolean lessThanMaxZ = scaledMouseZ < screenTileMinZ + size;
        return greaterThanMinX && greaterThanMinZ && lessThanMaxX && lessThanMaxZ;
    }

    public DataAtPosition getBiomeAtMousePosition(int mouseX, int mouseZ, int screenTileMinX, int screenTileMinZ) {
        return this.dataAtPos[mouseX - screenTileMinX][mouseZ - screenTileMinZ];
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ) {
        renderImage(stack, screenTileMinX, screenTileMinZ, this.texture);
        if (this.slimeChunks != null) {

            // TODO: Add
            RenderSystem.setShaderColor(1, 1, 1, 0.7F);
            renderImage(stack, screenTileMinX, screenTileMinZ, this.slimeChunks);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private void renderImage(PoseStack stack, int screenTileMinX, int screenTileMinZ, DynamicTexture texture) {
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
    }

    @Override
    public void close() {
        texture.close();
    }

    public void tick() {
    }

    record DataAtPosition(Holder<Biome> biomeHolder, BlockPos worldPos) {
    }
}
