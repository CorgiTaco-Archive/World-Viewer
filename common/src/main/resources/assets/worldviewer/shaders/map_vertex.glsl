#version 460 core

layout (location = 0) in vec4 position;

layout (binding = 0, column_major) uniform matrices {
    mat4 pvm;
};

layout (std430, binding = 0) readonly buffer shaderStorageBuffer {
    mat4 array[];
};

out gl_PerVertex {
    vec4 gl_Position;
};

void main() {
    mat4 data = array[gl_InstanceID];

    gl_Position = pvm * data * position;
}
