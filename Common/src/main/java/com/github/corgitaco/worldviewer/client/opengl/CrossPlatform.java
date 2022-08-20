package com.github.corgitaco.worldviewer.client.opengl;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import static org.lwjgl.opengl.ARBSeparateShaderObjects.glCreateShaderProgramv;

import static org.lwjgl.opengl.GL33.*;

public final class CrossPlatform {
    private static final GLCapabilities CAPABILITIES = GL.getCapabilities();

    private static final boolean DIRECT_STATE_ACCESS = CAPABILITIES.GL_ARB_direct_state_access;

    private static final boolean SEPARATE_SHADER_OBJECTS = CAPABILITIES.GL_ARB_separate_shader_objects;

    private CrossPlatform() {
    }

    public static int createProgram(int type, String source) {
        if (SEPARATE_SHADER_OBJECTS) {
            return glCreateShaderProgramv(type, source);
        } else {
            int program = glCreateProgram();

            int shader = glCreateShader(type);

            glShaderSource(shader, source);
            glCompileShader(shader);

            glAttachShader(program, shader);
            glLinkProgram(program);
            glDetachShader(program, shader);

            glDeleteShader(shader);

            glValidateProgram(program);

            return program;
        }
    }
}
