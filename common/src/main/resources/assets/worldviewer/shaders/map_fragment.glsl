#version 460 core

out vec4 color;

in vec2 inUv;

uniform sampler2DArray sampler;

void main() {
    color = texture(sampler, vec3(inUv, 1)) * vec4(1.0F, 1.0F, 1.0F, 1.0F);
}
