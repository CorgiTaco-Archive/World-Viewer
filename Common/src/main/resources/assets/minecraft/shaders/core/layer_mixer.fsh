#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float Opacity;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);

    vec4 color2 = texture(Sampler1, texCoord0);

    fragColor = vec4(color.rgb * color2.rgb, color.a * Opacity);
}