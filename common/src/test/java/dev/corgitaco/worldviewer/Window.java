package dev.corgitaco.worldviewer;

import dev.corgitaco.worldviewer.client.Destroyable;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;

public final class Window implements Destroyable {
    private final long window;

    private int width;
    private int height;

    {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6);

        if ((window = glfwCreateWindow(854, 480, "Rendering", NULL, NULL)) == NULL) {
            throw new RuntimeException();
        }

        try (var stack = MemoryStack.stackPush()) {
            var buffers = stack.callocInt(6);

            var address = memAddress(buffers);

            nglfwGetMonitorWorkarea(glfwGetPrimaryMonitor(), address, address + 4, address + 8, address + 12);
            nglfwGetWindowSize(window, address + 16, address + 20);

            glfwSetWindowPos(window,
                    (width = buffers.get(2) - buffers.get(4)) / 2,
                    (height = buffers.get(3) - buffers.get(5)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void update() {
        glfwPollEvents();
        glfwSwapBuffers(window);
    }

    @Override
    public void destroy() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
    }

    public float getAspectRatio() {
        return (float) width / (float) height;
    }
}
