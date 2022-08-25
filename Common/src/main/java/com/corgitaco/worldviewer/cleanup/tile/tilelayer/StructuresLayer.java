package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.*;

public class StructuresLayer extends TileLayer {

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = Collections.synchronizedMap(new HashMap<>());
    private final WorldScreenv2 screen;

    public StructuresLayer(Map<String, Object> cache, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen) {
        super(cache, y, tileWorldX, tileWorldZ, size, sampleResolution, level, screen);
        this.screen = screen;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) - x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) - z;
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);

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
    }

    @Override
    public void afterTilesRender(PoseStack stack, double mouseX, double mouseY, double mouseWorldX, double mouseWorldZ) {
        this.positionsForStructure.forEach(((configuredStructureFeatureHolder, longs) -> {
            this.screen.getStructureRendering().getOrDefault(configuredStructureFeatureHolder, ((stack1, scaledX, scaledZ) -> {}));
        }));
    }

    private static <FC extends FeatureConfiguration, F extends StructureFeature<FC>> boolean canCreate(ServerLevel level, ChunkGenerator generator, int x, int z, ConfiguredStructureFeature<FC, F> value) {
        return value.feature.canGenerate(level.registryAccess(), generator, generator.getBiomeSource(), level.getStructureManager(), level.getSeed(), new ChunkPos(x, z), value.config, level, value.biomes()::contains);
    }
}
