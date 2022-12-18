package com.corgitaco.worldviewer.cleanup.storage;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class DataTileBiomeStorage {

    private static final int SIZE = 4;


    public final int[] values;

    private Holder<Biome>[] lookUp;


    @SuppressWarnings("unchecked")
    public DataTileBiomeStorage(CompoundTag tag, Registry<Biome> biomeRegistry) {
        this.values = tag.getIntArray("values");

        this.lookUp = tag.getList("lookup", CompoundTag.TAG_STRING).stream().map(tag1 -> (StringTag) tag1).map(StringTag::getAsString).map(ResourceLocation::new)
                .map(location -> ResourceKey.create(Registry.BIOME_REGISTRY, location)).map(biomeRegistry::getHolderOrThrow).toArray(Holder[]::new);
    }

    public DataTileBiomeStorage(int[] values, Holder<Biome>[] lookUp) {
        this.values = values;
        this.lookUp = lookUp;
    }

    public DataTileBiomeStorage() {
        this(new int[SIZE * SIZE], new Holder[0]);
        Arrays.fill(values, -1);
    }

    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putIntArray("values", this.values);

        ListTag tag = new ListTag();
        for (Holder<Biome> biomeHolder : this.lookUp) {
            tag.add(StringTag.valueOf(biomeHolder.unwrapKey().orElseThrow().location().toString()));
        }

        compoundTag.put("lookup", tag);

        return compoundTag;
    }


    public Holder<Biome> getBiome(int x, int z, BiomeGetter getter) {
        Holder<Biome> biome = getBiome(x, z);

        if (biome == null) {
            Holder<Biome> holder = getter.get(x, z);

            int lookupIdx = -1;
            Holder<Biome>[] up = this.lookUp;
            for (int i = 0; i < up.length; i++) {
                Holder<Biome> biomeHolder = up[i];
                if (biomeHolder == holder) {
                    lookupIdx = i;
                    break;
                }
            }

            if (lookupIdx == -1) {
                this.lookUp = Arrays.copyOf(this.lookUp, this.lookUp.length + 1);
                this.lookUp[this.lookUp.length - 1] = holder;
                lookupIdx = this.lookUp.length - 1;
            }

            values[getIndex(x, z)] = lookupIdx;
            biome = holder;
        }

        return biome;
    }

    @Nullable
    private Holder<Biome> getBiome(int x, int z) {
        int value = values[getIndex(x, z)];
        if (value == -1) {
            return null;
        }
        return this.lookUp[value];
    }

    private static int getIndex(int x, int z) {
        return (x + z) * SIZE;
    }

    @FunctionalInterface
    interface BiomeGetter {

        Holder<Biome> get(int x, int z);
    }
}