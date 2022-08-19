package com.example.examplemod.client;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.GL33.*;

// Wip Instanced rendering.

public final class WorldScreenStructureSprite {
    private static final GLCapabilities CAPABILITIES = GL.getCapabilities();

    private static final boolean DIRECT_STATE_ACCESS = CAPABILITIES.GL_ARB_direct_state_access;

    private final int vao;
    private final int vbo;
    private final int ebo;

    WorldScreenStructureSprite() {
        if (DIRECT_STATE_ACCESS) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var buffer = stack.callocInt(2);
                vbo = buffer.get();
                ebo = buffer.get();
            }

            vao = glCreateVertexArrays();
        } else {
            vao = glGenVertexArrays();

            glBindVertexArray(vao);

            vbo = glCreateBuffers();
            glBindBuffer(GL_VERTEX_ARRAY, vbo);
            glBindBuffer(GL_VERTEX_ARRAY, 0);

            ebo = glCreateBuffers();
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

            glBindVertexArray(0);
        }
    }

    public void draw() {

        glBindVertexArray(vao);

        glDrawElementsInstanced(GL_TRIANGLES, 0, GL_UNSIGNED_INT, 0, 1);

        glBindVertexArray(0);
    }

    private void upload() {

        if (DIRECT_STATE_ACCESS) {

            glNamedBufferData(vbo, 0, GL_DYNAMIC_DRAW);

            glNamedBufferData(ebo, 0, GL_DYNAMIC_DRAW);
        } else {

            glBindBuffer(GL_VERTEX_ARRAY, vbo);
            glBufferData(GL_VERTEX_ARRAY, 0, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_VERTEX_ARRAY, 0);

            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, 0, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    public void close() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
    }
}
