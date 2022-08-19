package com.example.examplemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
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

import java.util.ArrayList;
import java.util.List;

public final class Tile implements AutoCloseable {
    public final DynamicTexture texture;
    private final int worldX;
    private final int worldZ;
    private final int size;

    private final DataAtPosition[][] dataAtPos;

    private final Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = new Object2ObjectOpenHashMap<>();


    public Tile(boolean heightMap, int ySample, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level, Object2IntOpenHashMap<Holder<Biome>> colorLookup) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;
        this.dataAtPos = new DataAtPosition[size][size];
        NativeImage nativeImage = new NativeImage(size, size, false);

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
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
        this.texture = new DynamicTexture(nativeImage);


        for (Holder<StructureSet> structureSetHolder : level.registryAccess().registryOrThrow(Registry.STRUCTURE_SET_REGISTRY).asHolderIdMap()) {
            StructureSet structureSet = structureSetHolder.value();

            for (int x = SectionPos.blockToSectionCoord(worldX); x <= SectionPos.blockToSectionCoord(worldX + size - 1); x++) {
                for (int z = SectionPos.blockToSectionCoord(worldZ); z <= SectionPos.blockToSectionCoord(worldZ + size - 1); z++) {
                    if (structureSet.placement().isFeatureChunk(generator, level.getSeed(), x, z)) {
                        WorldgenRandom worldgenrandom = new WorldgenRandom(new LegacyRandomSource(0L));
                        worldgenrandom.setLargeFeatureSeed(level.getSeed(), x, z);
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
                            if (canCreate(level, generator, x, z, value)) {
                                this.positionsForStructure.computeIfAbsent(configuredStructureFeatureHolder, key -> new LongArraySet()).add(ChunkPos.asLong(x, z));
                                return;
                            }

                            arraylist.remove(k);
                            i -= structureset$structureselectionentry3.weight();
                        }

                    }
                }
            }
        }
    }

    private static <FC extends FeatureConfiguration, F extends StructureFeature<FC>> boolean canCreate(ServerLevel level, ChunkGenerator generator, int x, int z, ConfiguredStructureFeature<FC, F> value) {
        return value.feature.canGenerate(level.registryAccess(), generator, generator.getBiomeSource(), level.getStructureManager(), level.getSeed(), new ChunkPos(x, z), value.config, level, value.biomes()::contains);
    }

    public Object2ObjectOpenHashMap<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> getPositionsForStructure() {
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
        RenderSystem.setShaderTexture(0, this.texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
    }

    @Override
    public void close() {
        texture.close();
    }

    record DataAtPosition(Holder<Biome> biomeHolder, BlockPos worldPos) {
    }
}
