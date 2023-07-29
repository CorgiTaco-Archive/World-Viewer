package dev.corgitaco.worldviewer;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwTerminate;

public final class WindowTest {

    @Test
    public void test() {

        if (!glfwInit()) {
            throw new RuntimeException();
        }

        var window = new Window();

        GL.createCapabilities();

        // Ignoring view matrix.

        while (!window.shouldClose()) {

            window.update();
        }

        window.destroy();

        glfwTerminate();
    }
}
