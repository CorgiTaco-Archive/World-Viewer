package com.example.examplemod;

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

//        Tile.IMAGES.add(((serverLevel, sizeX, sizeZ) -> new TerraBlenderImage(serverLevel.getSeed(), sizeX, sizeZ)));

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
}
