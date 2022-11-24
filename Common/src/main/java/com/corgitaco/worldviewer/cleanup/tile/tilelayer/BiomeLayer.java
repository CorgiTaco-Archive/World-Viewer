package com.corgitaco.worldviewer.cleanup.tile.tilelayer;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.common.WorldViewer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class BiomeLayer extends TileLayer {

    private final DynamicTexture biomes;

    public BiomeLayer(@Nullable CompoundTag compoundTag, Map<String, Object> cache, int y, int tileWorldX, int tileWorldZ, int size, int sampleResolution, ServerLevel level, WorldScreenv2 screen) {
        super(compoundTag, cache, y, tileWorldX, tileWorldZ, size, sampleResolution, level, screen);
        DynamicTexture dynamicTexture;

        if (compoundTag != null) {
            try {
                dynamicTexture = new DynamicTexture(NativeImage.read(ByteBuffer.wrap(compoundTag.getByteArray("data"))));
            } catch (IOException e) {
                e.printStackTrace();
                WorldViewer.LOGGER.error(String.format("Could not read biomes on disk. For tile {%s, %s}", tileWorldX, tileWorldZ));
                dynamicTexture = new DynamicTexture(buildImage(cache, y, tileWorldX, tileWorldZ, size, sampleResolution, level));
            }
        } else {
            dynamicTexture = new DynamicTexture(buildImage(cache, y, tileWorldX, tileWorldZ, size, sampleResolution, level));
        }
        this.biomes = dynamicTexture;
    }


    @NotNull
    private static NativeImage buildImage(Map<String, Object> cache, int y, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level) {
        Long2ObjectLinkedOpenHashMap<Holder<Biome>> biomes = (Long2ObjectLinkedOpenHashMap<Holder<Biome>>) cache.computeIfAbsent("biomes", o -> new Long2ObjectLinkedOpenHashMap<Holder<Biome>>());
        Long2IntOpenHashMap heights = (Long2IntOpenHashMap) cache.computeIfAbsent("heights", o -> new Long2IntOpenHashMap());

        NativeImage image = new NativeImage(size, size, true);
        boolean heightColoring = false;
        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, y, worldZ - sampleZ);

                long key = ChunkPos.asLong(sampleX, sampleZ);
                if (heights.containsKey(key)) {
                    y = heights.get(key);
                    if (!heightColoring) {
                        heightColoring = true;
                    }
                }

                Holder<Biome> biomeHolder = biomes.computeIfAbsent(key, s -> level.getBiome(worldPos));

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;
                        int color = COLORS.computeIfAbsent(biomeHolder.unwrapKey().orElseThrow(), biomeResourceKey -> {
                            Biome value = biomeHolder.value();
                            float baseTemperature = value.getBaseTemperature();
                            float lerp = Mth.inverseLerp(baseTemperature, -2, 2);
                            int r = (int) Mth.clampedLerp(137, 139, lerp);
                            int g = (int) Mth.clampedLerp(207, 0, lerp);
                            int b = (int) Mth.clampedLerp(240, 0, lerp);

                            return NativeImage.combine(255, b, g, r);
                        });

                        if (heightColoring) {
                            color = FastColor.ARGB32.multiply(color, HeightsLayer.getGrayScale(y, level.getChunkSource().getGenerator()));
                        }


                        image.setPixelRGBA(dataX, dataZ, color);
                    }
                }
            }
        }
        return image;
    }


    @Override
    public CompoundTag save() {
        CompoundTag compoundTag = new CompoundTag();
        if (this.biomes.getPixels() != null) {
            try {
                compoundTag.putByteArray("data", this.biomes.getPixels().asByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return compoundTag;
    }

    @Override
    public @Nullable Component toolTip(double mouseX, double mouseY, double worldX, double worldZ) {
        return null;
    }

    @Override
    public void afterTilesRender(PoseStack stack, double mouseX, double mouseY, double worldX, double worldZ) {

    }

    @Override
    public DynamicTexture getImage() {
        return this.biomes;
    }

    @Override
    public void close() {
        this.biomes.close();
    }


    public static final Object2IntOpenHashMap<ResourceKey<Biome>> COLORS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put(Biomes.BADLANDS, tryParseColor("0xD94515"));
        map.put(Biomes.BAMBOO_JUNGLE, tryParseColor("0x2C4205"));
        map.put(Biomes.BEACH, tryParseColor("0xFADE55"));
        map.put(Biomes.DESERT, tryParseColor("0xFA9418"));
        map.put(Biomes.BIRCH_FOREST, tryParseColor("0x307444"));
        map.put(Biomes.PLAINS, tryParseColor("0x8DB360"));
        map.put(Biomes.WINDSWEPT_HILLS, tryParseColor("0x606060"));
        map.put(Biomes.FOREST, tryParseColor("0x056621"));
        map.put(Biomes.TAIGA, tryParseColor("0x0B6659"));
        map.put(Biomes.SWAMP, tryParseColor("0x07F9B2"));
        map.put(Biomes.RIVER, tryParseColor("0x0000FF"));
        map.put(Biomes.NETHER_WASTES, tryParseColor("0xFF0000"));
        map.put(Biomes.THE_VOID, tryParseColor("0x8080FF"));
        map.put(Biomes.FROZEN_RIVER, tryParseColor("0xA0A0A0"));
        map.put(Biomes.SNOWY_PLAINS, tryParseColor("0xFFFFFF"));
        map.put(Biomes.MUSHROOM_FIELDS, tryParseColor("0xFF00FF"));
        map.put(Biomes.JUNGLE, tryParseColor("0x537B09"));
        map.put(Biomes.SPARSE_JUNGLE, tryParseColor("0x628B17"));
        map.put(Biomes.STONY_SHORE, tryParseColor("0xA2A284"));
        map.put(Biomes.SNOWY_BEACH, tryParseColor("0xFAF0C0"));
        map.put(Biomes.DARK_FOREST, tryParseColor("0x40511A"));
        map.put(Biomes.SNOWY_TAIGA, tryParseColor("0x31554A"));
        map.put(Biomes.OLD_GROWTH_PINE_TAIGA, tryParseColor("0x596651"));
        map.put(Biomes.OLD_GROWTH_SPRUCE_TAIGA, tryParseColor("0x818E79"));
        map.put(Biomes.SAVANNA, tryParseColor("0xBDB25F"));
        map.put(Biomes.SAVANNA_PLATEAU, tryParseColor("0xA79D24"));
        map.put(Biomes.WOODED_BADLANDS, tryParseColor("0xCA8C65"));
        map.put(Biomes.ERODED_BADLANDS, tryParseColor("0xFF6D3D"));
        map.put(Biomes.SUNFLOWER_PLAINS, tryParseColor("0xB5DB88"));
        map.put(Biomes.FLOWER_FOREST, tryParseColor("0x2D8E49"));
        map.put(Biomes.ICE_SPIKES, tryParseColor("0xB4DCDC"));
        map.put(Biomes.OCEAN, tryParseColor("0x000070"));
        map.put(Biomes.DEEP_OCEAN, tryParseColor("0x000030"));
        map.put(Biomes.COLD_OCEAN, tryParseColor("0x0056d6"));
        map.put(Biomes.DEEP_COLD_OCEAN, tryParseColor("0x004ecc"));
        map.put(Biomes.LUKEWARM_OCEAN, tryParseColor("0x45ADF2"));
        map.put(Biomes.DEEP_LUKEWARM_OCEAN, tryParseColor("0x3BA3E8"));
        map.put(Biomes.FROZEN_OCEAN, tryParseColor("0x9090A0"));
        map.put(Biomes.DEEP_FROZEN_OCEAN, tryParseColor("0x676791"));
        map.put(Biomes.FROZEN_PEAKS, tryParseColor("0x8BC0FC"));
        map.put(Biomes.WINDSWEPT_SAVANNA, tryParseColor("0xE5DA87"));
        map.put(Biomes.WINDSWEPT_FOREST, tryParseColor("0x589C6C"));
        map.put(Biomes.STONY_PEAKS, tryParseColor("0xC0C0C0"));
        map.put(Biomes.JAGGED_PEAKS, tryParseColor("0x969696"));
        map.put(Biomes.GROVE, tryParseColor("0x42FFBa"));
    });


    public static int tryParseColor(String input) {
        int result = Integer.MAX_VALUE;

        if (input.isEmpty()) {
            return result;
        }

        try {
            String colorSubString = input.substring(2);
            result = (255 << 24) | (Integer.parseInt(colorSubString, 16));

            int r = FastColor.ARGB32.red(result);
            int g = FastColor.ARGB32.green(result);
            int b = FastColor.ARGB32.blue(result);
            int a = FastColor.ARGB32.alpha(result);

            return NativeImage.combine(a, b, g, r);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return result;
    }
}
