package com.corgitaco.worldviewer.client;

import com.example.examplemod.Constants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class WVDynamicTexture extends DynamicTexture {
    public WVDynamicTexture(NativeImage $$0) {
        super($$0);
    }

    @Override
    public void upload() {
        NativeImage pixels = this.getPixels();
        if (pixels != null) {
            this.bind();
            pixels.upload(0, 0, 0, 0, 0, pixels.getWidth(), pixels.getHeight(), false, true, false, false);
        } else {
            Constants.LOGGER.warn("Trying to upload disposed texture {}", this.getId());
        }
    }
}
