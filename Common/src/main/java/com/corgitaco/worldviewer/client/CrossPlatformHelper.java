package com.corgitaco.worldviewer.client;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static org.lwjgl.opengl.GL33.*;

public final class CrossPlatformHelper {
    public static final GLCapabilities CAPABILITIES = GL.getCapabilities();

    public static final boolean DIRECT_STATE_ACCESS = CAPABILITIES.GL_ARB_direct_state_access;

    public static final boolean BUFFER_STORAGE = CAPABILITIES.GL_ARB_buffer_storage;

    public static final boolean SEPARATE_SHADER_OBJECTS = CAPABILITIES.GL_ARB_separate_shader_objects;

    private CrossPlatformHelper() {
    }

    public static int createShader(int type, String source) {
        var shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        return shader;
    }

    public static String read(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder builder = new StringBuilder();

            @Nullable
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }

            return builder.toString();
        }
    }

    public static final class UnsupportedGLExtensionException extends RuntimeException {

        public UnsupportedGLExtensionException(String reason) {
            super(reason);
        }
    }
}
