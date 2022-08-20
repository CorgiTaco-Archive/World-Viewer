#version 330

layout (location = 0) in vec4 position;
layout (location = 1) in vec2 uv;

uniform mat4 projection;
uniform mat4 modelView;

out vec2 texture_uv;

out gl_PerVertex {
    vec4 gl_Position;
};

void main() {
    gl_Position = projection * modelView * position;
    texture_uv = uv;
}
