package com.example.examplemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import static java.util.Objects.requireNonNull;
import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.rpmalloc.RPmalloc.*;

// Wip Instanced rendering.

public final class WorldScreenStructureSprite {
    private static final GLCapabilities CAPABILITIES = GL.getCapabilities();

    private static final boolean DIRECT_STATE_ACCESS = CAPABILITIES.GL_ARB_direct_state_access;

    private final int vao;
    private final int vbo;
    private final int ebo;

    WorldScreenStructureSprite() {
        if (!CAPABILITIES.GL_ARB_draw_instanced) {
            throw new UnsupportedGLExtensionException("GL_ARB_draw_instanced is required.");
        }

        rpmalloc_initialize();
        rpmalloc_thread_initialize();

        var buffer = requireNonNull(rpaligned_calloc(4, 1, (24 + 6) * 4));

        buffer.putFloat(-0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(0.0F);
        buffer.putFloat( 0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(0.0F);
        buffer.putFloat( 0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(0.0F);
        buffer.putFloat(-0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F).putFloat(0.0F).putFloat(0.0F);

        buffer.putInt(0).putInt(1).putInt(2).putInt(2).putInt(3).putInt(0);

        buffer.flip();

        var vertices = 24 * 4;
        var elements = 6 * 4;

        if (DIRECT_STATE_ACCESS) {
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

            vbo = glCreateBuffers();
            glBindBuffer(GL_VERTEX_ARRAY, vbo);
            glBufferData(GL_VERTEX_ARRAY, buffer, GL_STATIC_DRAW);
            glBindBuffer(GL_VERTEX_ARRAY, 0);

            ebo = glCreateBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glBindVertexArray(0);
        }

        rpfree(buffer);
    }

    public void draw() {
        var shader = RenderSystem.getShader();

        glUseProgram(0);

        glBindVertexArray(vao);

        if (DIRECT_STATE_ACCESS) {
            glEnableVertexArrayAttrib(vao, 0);
            glEnableVertexArrayAttrib(vao, 1);
        } else {
            glEnableVertexAttribArray(0);
            glEnableVertexAttribArray(1);
        }

        glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, 1);

        if (DIRECT_STATE_ACCESS) {
            glDisableVertexArrayAttrib(vao, 0);
            glDisableVertexArrayAttrib(vao, 1);
        } else {
            glDisableVertexAttribArray(0);
            glDisableVertexAttribArray(1);
        }

        glBindVertexArray(0);

        glUseProgram(shader.getId());
    }

    public void close() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);

        rpmalloc_thread_finalize(true);
        rpmalloc_finalize();
    }

    private static final class UnsupportedGLExtensionException extends RuntimeException {

        private UnsupportedGLExtensionException(String reason) {
            super(reason);
        }
    }
}
