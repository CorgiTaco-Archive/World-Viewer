package com.corgitaco.worldviewer.cleanup.tile;

import com.corgitaco.worldviewer.cleanup.WorldScreenv2;
import com.corgitaco.worldviewer.cleanup.tile.tilelayer.TileLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;

import java.util.*;

public class TileV2 {


    private final HashMap<String, TileLayer> tileLayers = new HashMap<>();

    public TileV2(Map<String, TileLayer.Factory> factories, int scrollY, int tileWorldX, int tileWorldZ, int size, int sampleRes, WorldScreenv2 worldScreenv2) {
        Map<String, Object> cache = new HashMap<>();
        factories.forEach((s, factory) -> tileLayers.put(s, factory.make(cache, scrollY, tileWorldX, tileWorldZ, size, sampleRes, worldScreenv2.level, worldScreenv2)));
    }


    public List<Component> toolTip(double mouseX, double mouseY, int mouseWorldX, int mouseWorldZ) {
        return this.tileLayers.values().stream().map(tileLayer -> tileLayer.toolTip(mouseX, mouseY, mouseWorldX, mouseWorldZ)).filter(Objects::nonNull).toList();
    }

    public void render(PoseStack stack, int screenTileMinX, int screenTileMinZ, Collection<String> toRender) {

    }
}
