#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

in float v_Tex_Index;

uniform sampler2D u_textures[12];

void main() {
    int index = int(v_Tex_Index);

    vec4 color = texture(u_textures[index], texCoord0);
    if (color.a == 0.0) {
        discard;
    }

    fragColor = color;
}
