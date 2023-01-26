#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform float Opacity;

uniform vec4 BiomeColor;

in vec2 texCoord0;

out vec4 fragColor;

void main() {

    vec4 color = texture(Sampler0, texCoord0);

    vec4 color2 = texture(Sampler1, texCoord0);

    vec4 result = vec4(color.rgb * color2.rgb, color.a * Opacity);
    if(BiomeColor.a <= 0.1) {
        fragColor = result;
    } else {
        if(BiomeColor.rgb == color2.rgb) {
            result = result * vec4(1.2, 1.2, 1.2, 1);
        } else {
            result = vec4(color.rgb * color2.rgb, color.a * Opacity) * vec4(0.25, 0.25, 0.25, 1);
        }

        fragColor = result;
    }
}