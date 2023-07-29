package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Mesh;
import dev.corgitaco.worldviewer.client.ProgramPipeline;
import dev.corgitaco.worldviewer.client.Resources;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

public final class WindowTest {

    @Test
    public void test() throws IOException {

        if (!glfwInit()) {
            throw new RuntimeException();
        }

        var window = new Window();

        GL.createCapabilities();

        // Ignoring view matrix.
        var projection = new Matrix4f();
        var model = new Matrix4f();

        var mesh = new Mesh<InstantiableEntity>();

        // glClearColor(1.0F, 0.0F, 0.0F, 0.0F);

        var pipeline = new ProgramPipeline(
                Resources.readString("assets/worldviewer/shaders/map_fragment.glsl"),
                Resources.readString("assets/worldviewer/shaders/map_vertex.glsl"));

        var entities = List.of(new InstantiableEntity());

        while (!window.shouldClose()) {
            glClear(GL_COLOR_BUFFER_BIT);

            projection.identity();
            model.identity();

            mesh.draw(pipeline, entities);

            window.update();
        }

        window.destroy();

        glfwTerminate();
    }
}
