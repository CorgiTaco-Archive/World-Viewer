package com.corgitaco.worldviewer.client;

import com.corgitaco.worldviewer.common.WorldViewer;
import com.corgitaco.worldviewer.mixin.NativeImageAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.ARBBufferStorage.glBufferStorage;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glProgramUniform1i;
import static org.lwjgl.opengl.ARBSeparateShaderObjects.glProgramUniformMatrix4fv;
import static org.lwjgl.opengl.GL33.*;

// Wip Instanced rendering.

public final class WorldScreenStructureSprites {
    private static final ResourceLocation FRAGMENT_PROGRAM_SOURCE = WorldViewer.createResourceLocation("shaders/structure_sprites/fragment.glsl");
    private static final ResourceLocation VERTEX_PROGRAM_SOURCE = WorldViewer.createResourceLocation("shaders/structure_sprites/vertex.glsl");

    private final int vao;
    private final int vbo;
    private final int ebo;

    private final int program;
    private final int projectionUniform;
    private final int modelViewUniform;
    private final int samplerUniform;

    private final Texture texture = new Texture();

    WorldScreenStructureSprites() {
        if (!CrossPlatformHelper.CAPABILITIES.GL_ARB_draw_instanced) {
            throw new CrossPlatformHelper.UnsupportedGLExtensionException("GL_ARB_draw_instanced is required.");
        }

        var buffer = createByteBuffer().flip();

        var vertices = 24 * 4;
        var elements = 6 * 4;

        if (CrossPlatformHelper.DIRECT_STATE_ACCESS) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var buffers = stack.callocInt(2);

                glCreateBuffers(buffers);

                vbo = buffers.get();
                ebo = buffers.get();
            }

            vao = glCreateVertexArrays();

            var flags = GL_MAP_READ_BIT;

            buffer.limit(vertices);
            glNamedBufferStorage(vbo, buffer, flags);
            buffer.position(vertices);

            buffer.limit(vertices + elements);
            glNamedBufferStorage(ebo, buffer, flags);
            buffer.position(0);

            glVertexArrayVertexBuffer(vao, 0, vbo, 0, 24);

            glVertexArrayAttribFormat(vao, 0, 4, GL_FLOAT, false, 0);
            glVertexArrayAttribFormat(vao, 1, 2, GL_FLOAT, false, 16);

            glVertexArrayAttribBinding(vao, 0, 0);
            glVertexArrayAttribBinding(vao, 1, 0);

            glVertexArrayElementBuffer(vao, ebo);
        } else {
            vao = glGenVertexArrays();

            glBindVertexArray(vao);

            buffer.limit(vertices);
            glBindBuffer(GL_ARRAY_BUFFER, vbo = glGenBuffers());
            if (CrossPlatformHelper.BUFFER_STORAGE) {
                glBufferStorage(GL_ARRAY_BUFFER, buffer, GL_MAP_READ_BIT);
            } else {
                glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            }
            glVertexAttribPointer(0, 4, GL_FLOAT, false, 24, 0);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, 24, 16);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
            buffer.position(vertices);

            buffer.limit(vertices + elements);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo = glGenBuffers());
            if (CrossPlatformHelper.BUFFER_STORAGE) {
                glBufferStorage(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_MAP_READ_BIT);
            } else {
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            }
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            buffer.position(0);

            glBindVertexArray(0);
        }

        MemoryUtil.memFree(buffer);

        var manager = Minecraft.getInstance().getResourceManager();

        String fragmentSource = "";
        String vertexFragment = "";
        try {
            fragmentSource = CrossPlatformHelper.read(manager.getResource(FRAGMENT_PROGRAM_SOURCE).getInputStream());
            vertexFragment = CrossPlatformHelper.read(manager.getResource(VERTEX_PROGRAM_SOURCE).getInputStream());

        } catch (IOException e) {
            e.printStackTrace();
        }

        program = glCreateProgram();

        var fragment = CrossPlatformHelper.createShader(GL_FRAGMENT_SHADER, fragmentSource);
        var vertex = CrossPlatformHelper.createShader(GL_VERTEX_SHADER, vertexFragment);

        glAttachShader(program, fragment);
        glAttachShader(program, vertex);
        glLinkProgram(program);
        glDetachShader(program, fragment);
        glDetachShader(program, vertex);

        glDeleteShader(fragment);
        glDeleteShader(vertex);

        glValidateProgram(program);

        projectionUniform = glGetUniformLocation(program, "projection");
        modelViewUniform = glGetUniformLocation(program, "modelView");
        samplerUniform = glGetUniformLocation(program, "sampler");
    }

    // Gets freed after uploaded to VBO and EBO.
    private ByteBuffer createByteBuffer() {
        var buffer = MemoryUtil.memCalloc((24 + 6) * 4);
        buffer.putFloat(-0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(1.0F);
        buffer.putFloat( 0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(1.0F).putFloat(1.0F);
        buffer.putFloat( 0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(1.0F).putFloat(0.0F);
        buffer.putFloat(-0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(0.0F);

        buffer.putInt(0).putInt(1).putInt(2).putInt(2).putInt(3).putInt(0);

        return buffer;
    }

    public void draw(Matrix4f projection, Matrix4f modelView) {
        var shader = RenderSystem.getShader();

        glUseProgram(program);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var buffer = stack.callocFloat(16);

            if (CrossPlatformHelper.SEPARATE_SHADER_OBJECTS) {
                projection.store(buffer);
                glProgramUniformMatrix4fv(program, projectionUniform, false, buffer);
                modelView.store(buffer);
                glProgramUniformMatrix4fv(program, modelViewUniform, false, buffer);
            } else {
                projection.store(buffer);
                glUniformMatrix4fv(projectionUniform, false, buffer);
                modelView.store(buffer);
                glUniformMatrix4fv(modelViewUniform, false, buffer);
            }
        }

        if (CrossPlatformHelper.SEPARATE_SHADER_OBJECTS) {
            glProgramUniform1i(program, samplerUniform, 1);
        } else {
            glUniform1f(samplerUniform, 1);
        }

        glBindVertexArray(vao);

        if (CrossPlatformHelper.DIRECT_STATE_ACCESS) {
            glEnableVertexArrayAttrib(vao, 0);
            glEnableVertexArrayAttrib(vao, 1);
        } else {
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);

            glEnableVertexAttribArray(0);
            glEnableVertexAttribArray(1);
        }

        glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, 1);

        if (CrossPlatformHelper.DIRECT_STATE_ACCESS) {
            glDisableVertexArrayAttrib(vao, 0);
            glDisableVertexArrayAttrib(vao, 1);
        } else {
            glDisableVertexAttribArray(0);
            glDisableVertexAttribArray(1);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        glBindVertexArray(0);

        glUseProgram(shader.getId());
    }

    public void close() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);

        glDeleteProgram(program);

        texture.destroy();
    }

    private static final class Texture {
        private static final ResourceLocation DESERT_PYRAMID = new ResourceLocation("worldview/icon/structure/desert_pyramid.png");
        private static final ResourceLocation JUNGLE_PYRAMID = new ResourceLocation("worldview/icon/structure/jungle_pyramid.png");

        private final int texture;

        private Texture() {
            var manager = Minecraft.getInstance().getResourceManager();

            if (CrossPlatformHelper.DIRECT_STATE_ACCESS) {
                texture = glCreateTextures(GL_TEXTURE_2D_ARRAY);

                glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);

                glTextureStorage3D(texture, 1, GL_RGBA8, 24, 24, 1);

                try (NativeImage image = NativeImage.read(manager.getResource(DESERT_PYRAMID).getInputStream())) {
                    glTextureSubImage3D(texture, 0, 0, 0, 0, image.getWidth(), image.getHeight(), 1, GL_RGBA, GL_UNSIGNED_BYTE, ((NativeImageAccessor) (Object) image).wvgetPixels());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                glBindTextureUnit(1, texture);
            } else {
                texture = glGenTextures();

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D_ARRAY, texture);

                glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);

                glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
            }
        }

        private void destroy () {
            glDeleteTextures(texture);
        }
    }
}
