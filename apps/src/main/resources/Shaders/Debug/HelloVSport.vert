/*
 * vertex shader for the Debug/HelloVSport program
 */
#version 450

layout(location = 0) in vec3 inPosition;  // positions from a vertex buffer
layout(location = 1) in vec2 inTexCoords; // texture coordinates from a vertex buffer

layout(location = 1) out vec2 texCoords;  // texture coordinates to the frag shader

layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

void main() {
    // vertex position in clip space
    gl_Position = ubo.proj * ubo.view * ubo.model * vec4(inPosition, 1.0);

    // interpolated texture coordinates
    texCoords = inTexCoords;
}
