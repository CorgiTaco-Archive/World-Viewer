#version 330

out vec4 color;

in vec2 frag_uv;

uniform sampler2DArray sampler;

void main() {
    color = texture(sampler, vec3(frag_uv, 1)) * vec4(1.0F, 1.0F, 1.0F, 1.0F);
}
