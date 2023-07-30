package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Mesh;
import dev.corgitaco.worldviewer.client.ProgramPipeline;
import dev.corgitaco.worldviewer.client.Resources;
import dev.corgitaco.worldviewer.client.TextureArray;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

import java.io.IOException;
import java.util.List;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.glClear;

public final class Tests {

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

        var textureArray = new TextureArray(64, 64, 3);
        textureArray.upload("blue.png", 0);
        textureArray.upload("green.png", 1);
        textureArray.upload("red.png", 2);

        textureArray.bind(0);

        // glClearColor(1.0F, 0.0F, 0.0F, 0.0F);

        var pipeline = new ProgramPipeline(
                Resources.readString("assets/worldviewer/shaders/map_fragment.glsl"),
                Resources.readString("assets/worldviewer/shaders/map_vertex.glsl"));

        var entities = List.of(new InstantiableEntity(), new InstantiableEntity());

        while (!window.shouldClose()) {
            glClear(GL_COLOR_BUFFER_BIT);

            var aspectRatio = window.getAspectRatio();
            var fieldOfView = (float) Math.toRadians(70.0F);

            projection.identity();
            projection.ortho(-fieldOfView * aspectRatio, fieldOfView * aspectRatio, -fieldOfView, fieldOfView, 1.0F, -1.0F);

            model.identity();

            mesh.uploadMatrices(projection.mul(model, new Matrix4f()));
            mesh.uploadInstances(entities);
            mesh.draw(pipeline, entities);

            window.update();
        }

        window.destroy();

        glfwTerminate();
    }
}
