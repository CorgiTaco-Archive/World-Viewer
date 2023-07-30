package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.mixin.NativeImageAccessor;

import java.io.IOException;

import static org.lwjgl.opengl.GL45.*;

public final class TextureArray {
    private final int texture = glCreateTextures(GL_TEXTURE_2D_ARRAY);
    private final int width;
    private final int height;

    public TextureArray(int width, int height, int depth) {
        this.width = width;
        this.height = height;

        glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTextureStorage3D(texture, 1, GL_RGBA8, width, height, depth);
    }

    public void bind(int unit) {
        glBindTextureUnit(unit, texture);
    }

    public void upload(NativeImage image, int z) throws IOException {
        if (image.getHeight() != height && image.getWidth() != width) {
            // TODO:: Warn
        }
        glTextureSubImage3D(texture, 0, 0, 0, z, image.getWidth(), image.getHeight(), 1, GL_RGBA, GL_UNSIGNED_BYTE, ((NativeImageAccessor) (Object) image).pixels());
    }

    public void upload(long pixels, int z, int width, int height) {
        glTextureSubImage3D(texture, 0, 0, 0, z, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }
}
