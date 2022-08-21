package com.example.examplemod;

import com.corgitaco.worldviewer.client.Tile;
import com.example.examplemod.network.FabricNetworkHandler;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.worldgen.noise.Area;
import terrablender.worldgen.noise.LayeredNoiseUtil;

import java.util.List;
import java.util.Random;

public class ExampleModFabric implements ModInitializer {

    @Override
    public void onInitialize() {

        Tile.IMAGES.add(((serverLevel, sizeX, sizeZ) -> new TerraBlenderImage(serverLevel.getSeed(), sizeX, sizeZ)));

        // This method is invoked by the Fabric mod loader when it is ready
        // to load your mod. You can access Fabric and Common code in this
        // project.

        // Use Fabric to bootstrap the Common mod.
        Constants.LOGGER.info("Hello Fabric world!");
        CommonClass.init();
        FabricNetworkHandler.init();

        // Some code like events require special initialization from the
        // loader specific code.
        ItemTooltipCallback.EVENT.register(CommonClass::onItemTooltip);
    }

    public static class TerraBlenderImage extends Tile.TileImage {

        private final Area uniqueness;

        private final NativeImage image;

        private final Int2IntOpenHashMap uniquenessColors = new Int2IntOpenHashMap();

        public TerraBlenderImage(long seed, int sizeX, int sizeZ) {
            super(seed, sizeX, sizeZ);
            this.uniqueness = LayeredNoiseUtil.uniqueness(RegionType.OVERWORLD, seed);
            List<Region> regions = Regions.get(RegionType.OVERWORLD);
            this.image = new NativeImage(sizeX, sizeZ, true);
            for (int i = 0; i < regions.size(); i++) {
                Region region = regions.get(i);
                Random random = new Random(region.getName().hashCode());

                int r = Mth.randomBetweenInclusive(random, 20, 255);
                int g = Mth.randomBetweenInclusive(random, 20, 255);
                int b = Mth.randomBetweenInclusive(random, 20, 255);
                uniquenessColors.put(i, FastColor.ARGB32.color(255, r, g, b));
            }

        }

        @Override
        public void forWorldCoords(int sampleX, int sampleZ, int worldX, int worldZ, int sampleResolution) {
            int i = this.uniqueness.get(worldX, worldZ);

            for (int x = 0; x < sampleResolution; x++) {
                for (int z = 0; z < sampleResolution; z++) {
                    this.image.setPixelRGBA(sampleX + x, sampleZ + z, this.uniquenessColors.get(i));
                }
            }
        }

        @Override
        protected DynamicTexture getTexture() {
            return new DynamicTexture(this.image);
        }
    }
}
