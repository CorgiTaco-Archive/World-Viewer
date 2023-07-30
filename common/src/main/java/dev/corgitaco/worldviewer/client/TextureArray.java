package dev.corgitaco.worldviewer.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.corgitaco.worldviewer.mixin.NativeImageAccessor;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;

import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.stb.STBImage.nstbi_image_free;
import static org.lwjgl.stb.STBImage.nstbi_load_from_memory;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memFree;

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

    public void upload(String path, int z) throws IOException {
        try (var stack = MemoryStack.stackPush()) {
            var buffers = stack.callocInt(3);

            var address = memAddress(buffers);

            var image = Resources.readImage(path);
            var pixels = nstbi_load_from_memory(memAddress(image), image.remaining(), address, address + 4, address + 8, 4);

            upload(pixels, z, buffers.get(0), buffers.get(1));

            memFree(image);
            nstbi_image_free(pixels);
        }
    }

    public void upload(long pixels, int z, int width, int height) {
        glTextureSubImage3D(texture, 0, 0, 0, z, width, height, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
    }
}
