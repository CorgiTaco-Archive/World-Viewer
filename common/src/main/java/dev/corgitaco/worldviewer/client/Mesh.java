package dev.corgitaco.worldviewer.client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.List;

import static org.lwjgl.opengl.GL45.*;
import static org.lwjgl.system.MemoryUtil.memCalloc;
import static org.lwjgl.system.MemoryUtil.memFree;

public final class Mesh<T extends Instantiable> implements Destroyable {
    private final int vao = glCreateVertexArrays();
    private final int vbo;
    private final int ebo;
    private final int ubo;
    private final int ssbo;

    {
        try (var stack = MemoryStack.stackPush()) {
            var buffers = stack.callocInt(4);

            glCreateBuffers(buffers);

            vbo = buffers.get(0);
            ebo = buffers.get(1);
            ubo = buffers.get(2);
            ssbo = buffers.get(3);

            setup();
        }
    }

    private void setup() {
        glVertexArrayVertexBuffer(vao, 0, vbo, 0, 16);

        var buffer = memCalloc((4 * 4 + 6) * 4);

        buffer.putFloat(-0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F);
        buffer.putFloat( 0.5F).putFloat(-0.5F).putFloat(0.0F).putFloat(1.0F);
        buffer.putFloat( 0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F);
        buffer.putFloat(-0.5F).putFloat( 0.5F).putFloat(0.0F).putFloat(1.0F);

        buffer.putInt(0).putInt(1).putInt(2).putInt(2).putInt(3).putInt(0);

        buffer.flip();

        var vertices = 4 * 4 * 4;
        var elements = 6 * 4;

        var flags = 0;

        buffer.limit(vertices);
        glNamedBufferStorage(vbo, buffer, flags);
        buffer.position(vertices);

        buffer.limit(vertices + elements);
        glNamedBufferStorage(ebo, buffer, flags);
        buffer.position(0);

        memFree(buffer);

        glVertexArrayElementBuffer(vao, ebo);
    }

    public void draw(ProgramPipeline pipeline, List<T> collection) {
        pipeline.bind();

        glBindVertexArray(vao);

        glEnableVertexArrayAttrib(vao, 0);

        glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, collection.size());

        glDisableVertexArrayAttrib(vao, 0);

        glBindVertexArray(0);

        ProgramPipeline.unbind();
    }

    // Multiply matrices before uploading to save overhead.
    public void uploadMatrices(Matrix4f matrix4f) {
        var mapped = glMapNamedBuffer(ubo, GL_READ_WRITE);
        if (mapped != null) {
            matrix4f.get(mapped);
        }

        glUnmapNamedBuffer(ubo);
    }

    public void uploadInstantiable(List<T> collection) {
        for (var i = 0; i < collection.size(); i++) {

        }
    }

    @Override
    public void destroy() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteBuffers(ubo);
        glDeleteBuffers(ssbo);
    }
}
