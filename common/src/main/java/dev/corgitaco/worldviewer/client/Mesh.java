package dev.corgitaco.worldviewer.client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL45.*;

public final class Mesh implements Destroyable {
    private final int vao = glCreateVertexArrays();
    private final int vbo;
    private final int ebo;
    private final int ubo;

    {
        try (var stack = MemoryStack.stackPush()) {
            var buffers = stack.callocInt(3);

            glCreateBuffers(buffers);

            vbo = buffers.get(0);
            ebo = buffers.get(1);
            ubo = buffers.get(3);
        }
    }

    public void draw(ProgramPipeline pipeline) {
        pipeline.bind();

        glDrawElementsInstanced(GL_TRIANGLES, 0, GL_UNSIGNED_INT, 0, 1);

        ProgramPipeline.unbind();
    }

    // Multiply matrices before uploading to save overhead.
    public void uploadMatrices(Matrix4f matrix4f) {

    }

    @Override
    public void destroy() {
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteBuffers(ubo);
    }
}
