package com.corgitaco.worldviewer.common;

import com.corgitaco.worldviewer.client.WorldScreenStructureSpritesReloadListener;
import com.corgitaco.worldviewer.common.WorldViewer;
import com.example.examplemod.network.ForgeNetworkHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
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
        bus.addListener(this::registerReloadListener);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ForgeNetworkHandler.init();
    }

    private void clientSetup(FMLClientSetupEvent event) {
    }

    private void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new WorldScreenStructureSpritesReloadListener());
    }
}
