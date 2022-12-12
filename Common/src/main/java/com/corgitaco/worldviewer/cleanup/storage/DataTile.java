package com.corgitaco.worldviewer.cleanup.storage;


import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import org.jetbrains.annotations.NotNull;

import java.util.*;

//TODO: Add structures.
public class DataTile {

    private static final int SIZE = 16;

    private final Map<Heightmap.Types, int[]> heights = new EnumMap<>(Heightmap.Types.class);

    private final Holder<Biome>[] biomes = new Holder[QuartPos.fromBlock(SIZE)];

    private Set<Holder<ConfiguredStructureFeature<?, ?>>> structures = null;

    private final long pos;
    private final DataTileManager manager;

    private final boolean isSlimeChunk;

    private boolean needsSaving = false;

    public DataTile(long pos, DataTileManager tileManager, CompoundTag tag) {
        this.pos = pos;
        this.manager = tileManager;
        {
            ListTag list = tag.getList("heights", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < list.size(); i++) {
                IntArrayTag intTags = (IntArrayTag) list.get(i);
                heights.put(Heightmap.Types.values()[i], intTags.getAsIntArray());
            }
        }
        {
            ListTag biomes = tag.getList("biomes", Tag.TAG_STRING);
            for (int i = 0; i < biomes.size(); i++) {
                StringTag biome = (StringTag) biomes.get(i);
                Optional<Holder<Biome>> holder = tileManager.serverLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolder(ResourceKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(biome.getAsString())));
                if (holder.isPresent()) {
                    this.biomes[i] = holder.get();
                }
            }
        }
        {
            if (tag.contains("structures")) {
                this.structures = new ObjectOpenHashSet<>();
                ListTag structures = tag.getList("structures", Tag.TAG_STRING);
                for (Tag value : structures) {
                    StringTag structure = (StringTag) value;
                    Optional<Holder<ConfiguredStructureFeature<?, ?>>> holder = tileManager.serverLevel().registryAccess().registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY).getHolder(ResourceKey.create(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY, new ResourceLocation(structure.getAsString())));
                    holder.ifPresent(configuredStructureFeatureHolder -> this.structures.add(configuredStructureFeatureHolder));
                }
            }
        }

        this.isSlimeChunk = tag.getBoolean("slimes");

    }


    public DataTile(long pos, DataTileManager manager) {
        this.pos = pos;
        this.manager = manager;
        this.isSlimeChunk = manager.isSlimeChunkRaw(ChunkPos.getX(pos), ChunkPos.getZ(pos));
    }

    public int getHeight(Heightmap.Types type, int x, int z) {
        x = x & (SIZE - 1);
        z = z & (SIZE - 1);
        if (!heights.containsKey(type)) {
            int[] ints = new int[SIZE];
            Arrays.fill(ints, Integer.MIN_VALUE);
            heights.put(type, ints);
        }

        int[] heights = this.heights.get(type);
        int index = getIndex(x, z);
        int height = heights[index];

        if (height == Integer.MIN_VALUE) {
            height = this.manager.getHeightRaw(type, toWorldX(x), toWorldZ(z));
            heights[index] = height;
            needsSaving = true;
        }

        return height;
    }

    public Set<Holder<ConfiguredStructureFeature<?, ?>>> structures() {
        if (this.structures == null) {
            needsSaving = true;
            this.structures = this.manager.getStructuresRaw(this.pos);
        }
        return this.structures;
    }

    public Holder<Biome> getBiome(int x, int z) {
        x = x & (SIZE - 1);
        z = z & (SIZE - 1);

        x = QuartPos.fromBlock(x);
        z = QuartPos.fromBlock(z);

        x = QuartPos.toBlock(x);
        z = QuartPos.toBlock(z);

        int biomeIndex = getBiomeIndex(x, z);
        Holder<Biome> biome = biomes[biomeIndex];
        if (biome == null) {
            Holder<Biome> biomeRaw = this.manager.getBiomeRaw(toWorldX(x), toWorldZ(z));
            biomes[biomeIndex] = biomeRaw;
            biome = biomeRaw;
            needsSaving = true;
        }
        return biome;
    }

    private static int getIndex(int x, int z) {
        return (x + z) * SIZE;
    }

    private static int getBiomeIndex(int x, int z) {
        return QuartPos.fromBlock(getIndex(x, z));
    }

    public long getPos() {
        return pos;
    }

    private int toWorldX(int x) {
        return SectionPos.sectionToBlockCoord(ChunkPos.getX(pos), x);
    }

    private int toWorldZ(int x) {
        return SectionPos.sectionToBlockCoord(ChunkPos.getZ(pos), x);
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();

        compoundTag.put("heights", saveHeights());
        compoundTag.put("biomes", saveBiomes());
        compoundTag.put("structures", saveStructures());

        compoundTag.putBoolean("slimes", this.isSlimeChunk);
        return compoundTag;
    }

    @NotNull
    private ListTag saveStructures() {
        ListTag tags = new ListTag();

        if (structures != null) {
            for (Holder<ConfiguredStructureFeature<?, ?>> structure : this.structures) {
                tags.add(StringTag.valueOf(structure.unwrapKey().orElseThrow().location().toString()));
            }
        }
        return tags;
    }

    @NotNull
    private ListTag saveBiomes() {
        ListTag biomes = new ListTag();

        for (Holder<Biome> biome : this.biomes) {
            String biomeId = biome == null ? "null" : biome.unwrapKey().orElseThrow().location().toString();
            biomes.add(StringTag.valueOf(biomeId));
        }
        return biomes;
    }

    @NotNull
    private ListTag saveHeights() {
        ListTag heights = new ListTag();
        for (Heightmap.Types value : Heightmap.Types.values()) {
            heights.add(this.heights.containsKey(value) ? new IntArrayTag(this.heights.get(value)) : new IntArrayTag(new int[]{}));
        }
        return heights;
    }

    public boolean isSlimeChunk() {
        return isSlimeChunk;
    }

    public Set<Holder<ConfiguredStructureFeature<?, ?>>> getStructures() {
        return structures;
    }

    public boolean isNeedsSaving() {
        return needsSaving;
    }

    public void setNeedsSaving(boolean needsSaving) {
        this.needsSaving = needsSaving;
    }
}
