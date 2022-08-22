package com.corgitaco.worldviewer.client;

import com.corgitaco.worldviewer.mixin.MixinBiomeAccess;
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
import net.minecraft.util.Mth;
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
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Tile implements AutoCloseable {

    public static final boolean BETTER_HEIGHTMAP_COLOR_BLENDING = true;

    public static final List<TileImageMaker> IMAGES = new ArrayList<>();

    public final DynamicTexture biomes;
    public final DynamicTexture biomeTemps;
    private final int worldX;
    private final int worldZ;
    private final int size;

    private final DataAtPosition[][] dataAtPos;

    private final Map<Holder<ConfiguredStructureFeature<?, ?>>, LongSet> positionsForStructure = Collections.synchronizedMap(new HashMap<>());

    @Nullable
    public final DynamicTexture slimeChunks;

    @Nullable
    public final DynamicTexture heightmap;

    @Nullable
    private final DynamicTexture biomeHeights;


    private final List<DynamicTexture> textures = new ArrayList<>();

    public Tile(boolean computeHeightmap, int ySample, int worldX, int worldZ, int size, int sampleResolution, ServerLevel level, Object2IntMap<Holder<Biome>> colorLookup) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.size = size;
        this.dataAtPos = new DataAtPosition[size][size];

        List<TileImage> tileImages = new ArrayList<>();
        for (TileImageMaker image : IMAGES) {
            tileImages.add(image.make(level, size, size));
        }

        float minTemp = 0;
        float maxTemp = 0;

        for (Holder<Biome> biomeHolder : colorLookup.keySet()) {
            Biome value = biomeHolder.value();
            float baseTemperature = value.getBaseTemperature();
            if (minTemp > baseTemperature) {
                minTemp = baseTemperature;
            }

            if (maxTemp < baseTemperature) {
                maxTemp = baseTemperature;
            }
        }

        maxTemp += 0.5;
        minTemp -= 0.5;

        var generator = level.getChunkSource().getGenerator();

        NativeImage biomes = new NativeImage(size, size, true);
        NativeImage biomeTempsImg = new NativeImage(size, size, true);
        @Nullable
        NativeImage heightmapImg = computeHeightmap ? new NativeImage(size, size, true) : null;

        @Nullable
        NativeImage biomeHeightsImg = computeHeightmap ? new NativeImage(size, size, true) : null;

        BlockPos.MutableBlockPos worldPos = new BlockPos.MutableBlockPos();
        for (int sampleX = 0; sampleX < size; sampleX += sampleResolution) {
            for (int sampleZ = 0; sampleZ < size; sampleZ += sampleResolution) {
                worldPos.set(worldX - sampleX, 0, worldZ - sampleZ);

                int y = ySample;
                if (computeHeightmap) {
                    boolean hasChunk = level.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(worldPos.getX()), SectionPos.blockToSectionCoord(worldPos.getZ()));
                    if (hasChunk) {
                        y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldPos.getX(), worldPos.getZ());
                    } else {
                        y = generator.getBaseHeight(worldPos.getX(), worldPos.getZ(), Heightmap.Types.OCEAN_FLOOR, level);
                    }
                }
                worldPos.set(worldX - sampleX, y, worldZ - sampleZ);


                Holder<Biome> biomeHolder = level.getBiome(worldPos);
                Biome value = biomeHolder.value();
                float temp = ((MixinBiomeAccess) (Object) value).wv_getTemperature(worldPos);

                for (int x = 0; x < sampleResolution; x++) {
                    for (int z = 0; z < sampleResolution; z++) {
                        int dataX = sampleX + x;
                        int dataZ = sampleZ + z;


                        dataAtPos[dataX][dataZ] = new DataAtPosition(biomeHolder, new BlockPos(worldPos.getX() - x, y, worldPos.getZ() - z));
                        int biomeColor = colorLookup.getOrDefault(biomeHolder, 0);
                        biomes.setPixelRGBA(dataX, dataZ, biomeColor);
                        float tempPct = Mth.clamp(Mth.inverseLerp(temp, minTemp, maxTemp), 0, 1F);

                        int tempColor = Math.round(Mth.clampedLerp(255, 0, tempPct));

                        int tempGrayScale = FastColor.ARGB32.color(255, tempColor, tempColor, tempColor);

                        biomeTempsImg.setPixelRGBA(dataX, dataZ, tempGrayScale);

                        if (computeHeightmap) {
                            float pct = Mth.clamp(Mth.inverseLerp(y, generator.getMinY(), generator.getMinY() + generator.getGenDepth()), 0, 1F);
                            float a = Mth.clampedLerp(255, 0, pct);

                            int color = Math.round(a);
                            int grayScale = FastColor.ARGB32.color(255, color, color, color);
                            int mixed = FastColor.ARGB32.multiply(biomeColor, grayScale);

                            biomeHeightsImg.setPixelRGBA(dataX, dataZ, mixed);

                            heightmapImg.setPixelRGBA(dataX, dataZ, grayScale);
                        }
                    }
                }
                for (TileImage tileImage : tileImages) {
                    tileImage.forWorldCoords(sampleX, sampleZ, worldPos.getX(), worldPos.getZ(), sampleResolution);
                }
            }
        }

        for (TileImage tileImage : tileImages) {
            this.textures.add(tileImage.getTexture());
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
        this.biomes = new DynamicTexture(biomes);

        if (lazySlimeChunks == null) {
            this.slimeChunks = null;
        } else {
            this.slimeChunks = new DynamicTexture(lazySlimeChunks);
        }

        if (heightmapImg != null) {
            this.heightmap = new DynamicTexture(heightmapImg);
        } else {
            this.heightmap = null;
        }

        if (biomeHeightsImg != null) {
            this.biomeHeights = new DynamicTexture(biomeHeightsImg);
        } else {
            this.biomeHeights = null;
        }
        this.biomeTemps = new DynamicTexture(biomeTempsImg);
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

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, TileRenderType tileRenderType, boolean slimeChunks) {
        switch (tileRenderType) {
            case BIOMES -> {
                if (biomes != null) {
                    renderImage(stack, screenTileMinX, screenTileMinZ, this.biomes, 1);
                }
            }
            case HEIGHTMAP -> {
                if (heightmap != null) {
                    renderImage(stack, screenTileMinX, screenTileMinZ, this.heightmap, 1);
                }
            }
            case BIOME_HEIGHTMAP -> {
                if (biomeHeights != null) {
                    renderImage(stack, screenTileMinX, screenTileMinZ, this.biomeHeights, 1);
                }
            }
            case BIOME_TEMPERATURE -> renderImage(stack, screenTileMinX, screenTileMinZ, this.biomeTemps, 1);
        }

        if (this.slimeChunks != null && slimeChunks) {
            renderImage(stack, screenTileMinX, screenTileMinZ, this.slimeChunks, 0.9F);
        }

        for (DynamicTexture texture : this.textures) {
            renderImage(stack, screenTileMinX, screenTileMinZ, texture, 0.3F);
        }
    }

    private void renderImage(PoseStack stack, int screenTileMinX, int screenTileMinZ, DynamicTexture texture, float opacity) {
        if (texture.getPixels() == null) {
            return;
        }
        RenderSystem.setShaderColor(1, 1, 1, opacity);
        RenderSystem.setShaderTexture(0, texture.getId());
        RenderSystem.enableBlend();
        GuiComponent.blit(stack, screenTileMinX, screenTileMinZ, 0.0F, 0.0F, this.size, this.size, this.size, this.size);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Override
    public void close() {
        if (this.slimeChunks != null) {
            this.slimeChunks.close();
        }
        if (this.heightmap != null) {
            this.heightmap.close();
        }
        if (this.biomeHeights != null) {
            this.biomeHeights.close();
        }
        biomes.close();
        for (DynamicTexture texture : this.textures) {
            texture.close();
        }
        this.biomeTemps.close();
    }

    public void tick() {
    }

    record DataAtPosition(Holder<Biome> biomeHolder, BlockPos worldPos) {
    }

    public enum TileRenderType {
        BIOMES,
        HEIGHTMAP,
        BIOME_HEIGHTMAP,
        BIOME_TEMPERATURE
    }

    public abstract static class TileImage {

        private final long seed;
        private final int sizeX;
        private final int sizeZ;

        public TileImage(long seed, int sizeX, int sizeZ) {
            this.seed = seed;
            this.sizeX = sizeX;
            this.sizeZ = sizeZ;
        }

        public void forWorldCoords(int sampleX, int sampleZ, int worldX, int worldZ, int sampleResolution) {
        }

        protected abstract DynamicTexture getTexture();
    }

    @FunctionalInterface
    public interface TileImageMaker {

        TileImage make(ServerLevel serverLevel, int sizeX, int sizeZ);
    }
}
