package dev.corgitaco.worldviewer.client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
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
        glNamedBufferStorage(ubo, 16 * 4, GL_DYNAMIC_STORAGE_BIT | GL_MAP_READ_BIT | GL_MAP_WRITE_BIT);

        glBindBufferBase(GL_UNIFORM_BUFFER, 0, ubo);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssbo);

        glVertexArrayVertexBuffer(vao, 0, vbo, 0, 6 * 4);

        glVertexArrayAttribBinding(vao, 0, 0);
        glVertexArrayAttribBinding(vao, 1, 0);
        glVertexArrayAttribFormat(vao, 0, 4, GL_FLOAT, false, 0);
        glVertexArrayAttribFormat(vao, 1, 2, GL_FLOAT, false, 4 * 4);

        var buffer = memCalloc((6 * 4 + 6) * 4);

        putFloatArray(buffer, new float[] {
                -0.5F, -0.5F, 0.0F, 1.0F, 0.0F, 1.0F,
                 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 1.0F,
                 0.5F,  0.5F, 0.0F, 1.0F, 1.0F, 0.0F,
                -0.5F,  0.5F, 0.0F, 1.0F, 0.0F, 0.0F
        });

        putIntArray(buffer, new int[] {
                0, 1, 2,
                2, 3, 0
        });

        buffer.flip();

        var vertices = 6 * 4 * 4;
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

    public void draw(ProgramPipeline pipeline, List<T> list) {
        pipeline.bind();

        glBindVertexArray(vao);

        glEnableVertexArrayAttrib(vao, 0);
        glEnableVertexArrayAttrib(vao, 1);

        glDrawElementsInstanced(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0, list.size());

        glDisableVertexArrayAttrib(vao, 0);
        glDisableVertexArrayAttrib(vao, 1);

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

    public void uploadInstances(List<T> list) {
        var size = list.size();
        var buffer = memCalloc((16 * 4) * size);

        var matrix4f = new Matrix4f();

        for (var i = 0; i < size; i++) {
            var translation = list.get(i).getTranslation();

            matrix4f.identity();
            matrix4f.translate(translation.x, translation.y, 0.0F);

            matrix4f.get((16 * 4) * i, buffer);
        }

        glNamedBufferData(ssbo, buffer, GL_DYNAMIC_DRAW);

        memFree(buffer);
    }

    @Override
    public void destroy() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteBuffers(ubo);
        glDeleteBuffers(ssbo);
    }

    private static void putFloatArray(ByteBuffer buffer, float[] floats) {
        for (var aFloat : floats) {
            buffer.putFloat(aFloat);
        }
    }

    private static void putIntArray(ByteBuffer buffer, int[] ints) {
        for (var aInt : ints) {
            buffer.putInt(aInt);
        }
    }
}
