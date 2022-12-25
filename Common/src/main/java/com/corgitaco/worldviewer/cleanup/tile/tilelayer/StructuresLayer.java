package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.storage.DataTileManager;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class StructuresLayer extends TileLayer {

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = Collections.synchronizedMap(new HashMap<>());
    private final WorldScreenv2 screen;

    public StructuresLayer(DataTileManager tileManager, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, WorldScreenv2 screen) {
        super(tileManager, y, tileWorldX, tileWorldZ, size, sampleResolution, screen);
        this.screen = screen;

        for (int x = 0; x < SectionPos.blockToSectionCoord(size); x++) {
            for (int z = 0; z < SectionPos.blockToSectionCoord(size); z++) {
                int chunkX = SectionPos.blockToSectionCoord(tileWorldX) + x;
                int chunkZ = SectionPos.blockToSectionCoord(tileWorldZ) + z;
                long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                for (Holder<ConfiguredStructureFeature<?, ?>> structure : tileManager.getStructures(chunkX, chunkZ)) {
                    positionsForStructure.computeIfAbsent(structure, configuredStructureFeatureHolder -> new LongArraySet()).add(chunkKey);
                }
            }
        }
    }

    @Override
    public void afterTilesRender(PoseStack stack, double screenTileMinX, double screenTileMinZ, double mouseWorldX, double mouseWorldZ, double opacity) {

        this.positionsForStructure.forEach(((configuredStructureFeatureHolder, longs) -> {
            for (long structureChunkPos : longs) {
                int structureWorldX = SectionPos.sectionToBlockCoord(ChunkPos.getX(structureChunkPos)) - this.getTileWorldX();
                int structureWorldZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(structureChunkPos)) - this.getTileWorldZ();

                this.screen.getStructureRendering().get(configuredStructureFeatureHolder).render(stack, structureWorldX, structureWorldZ, structureWorldX + 15, structureWorldZ + 15, (float) opacity);
            }
        }));
    }

    @Override
    public @Nullable MutableComponent toolTip(double mouseScreenX, double mouseScreenY, int mouseWorldX, int mouseWorldZ, int mouseTileLocalX, int mouseTileLocalY) {
        StringBuilder structures = new StringBuilder();
        this.positionsForStructure.forEach((configuredStructureFeatureHolder, longs) -> {
            long mouseChunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(mouseWorldX), SectionPos.blockToSectionCoord(mouseWorldZ));
            if (longs.contains(mouseChunk)) {
                if (!structures.isEmpty()) {
                    structures.append(", ");
                }
                structures.append(configuredStructureFeatureHolder.unwrapKey().orElseThrow().location().toString());
            }
        });
        return new TextComponent("Structure(s): %s".formatted(structures.toString()));
    }
}
