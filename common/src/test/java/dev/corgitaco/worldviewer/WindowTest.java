package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Mesh;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.*;

public final class WindowTest {

    @Test
    public void test() {

        if (!glfwInit()) {
            throw new RuntimeException();
        }

        var window = new Window();

        GL.createCapabilities();

        // Ignoring view matrix.
        var projection = new Matrix4f();
        var model = new Matrix4f();

        var mesh = new Mesh();

        glClearColor(1.0F, 0.0F, 0.0F, 0.0F);

        while (!window.shouldClose()) {
            glClear(GL_COLOR_BUFFER_BIT);

            projection.identity();
            model.identity();

            mesh.draw(null);

            window.update();
        }

        window.destroy();

        glfwTerminate();
    }
}
