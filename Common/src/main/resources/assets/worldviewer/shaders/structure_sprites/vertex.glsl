#version 330

layout (location = 0) in vec4 position;
layout (location = 1) in vec2 uv;

out gl_PerVertex {
    vec4 gl_Position;
};

void main() {
    gl_Position = position;
}
