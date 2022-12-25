package com.corgitaco.worldviewer.common;

import com.example.examplemod.network.ForgeNetworkHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WorldViewer.MOD_ID)
public final class WorldViewerForge {

    public WorldViewerForge() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();

        bus.addListener(this::commonSetup);
        bus.addListener(this::clientSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ForgeNetworkHandler.init();
    }

    private void clientSetup(FMLClientSetupEvent event) {
    }
}
