#version 150

in vec3 Position;
in vec2 UV0;
in float Tex_Index;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out float v_Tex_Index;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;

    v_Tex_Index = Tex_Index;
}
