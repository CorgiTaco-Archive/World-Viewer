package dev.corgitaco.worldviewer;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;

public final class Window {
    private final long window;

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
                    (buffers.get(2) - buffers.get(4)) / 2,
                    (buffers.get(3) - buffers.get(5)) / 2);
        }

        glfwMakeContextCurrent(window);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void update() {
        glfwPollEvents();
        glfwSwapBuffers(window);
    }

    public void destroy() {
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
    }
}
