package dev.corgitaco.worldviewer.client;

import static org.lwjgl.opengl.GL45.*;

public final class ProgramPipeline implements Destroyable {
    private final int pipeline = glCreateProgramPipelines();
    private final int fragment;
    private final int vertex;

    public ProgramPipeline() {
        fragment = glCreateShaderProgramv(GL_FRAGMENT_SHADER, "");
        vertex = glCreateShaderProgramv(GL_VERTEX_SHADER, "");

        glUseProgramStages(pipeline, GL_FRAGMENT_SHADER_BIT, fragment);
        glUseProgramStages(pipeline, GL_VERTEX_SHADER_BIT, vertex);
        glValidateProgramPipeline(pipeline);
    }

    public void bind() {
        if (!glIsProgramPipeline(pipeline)) {
            // Warn.
        }
        glBindProgramPipeline(pipeline);
    }

    @Override
    public void destroy() {
        glDeleteProgramPipelines(0);
    }

    public static void unbind() {
        glBindProgramPipeline(0);
    }
}
