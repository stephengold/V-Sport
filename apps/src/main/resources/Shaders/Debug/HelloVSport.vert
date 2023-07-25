/*
 * vertex shader for the Debug/HelloVSport program
 */
#version 450

layout(location = 0) in vec3 inPosition;  // locations from vertex buffer
layout(location = 1) in vec2 inTexCoords; // texture coords from vertex buffer

layout(location = 1) out vec2 texCoords;    // texture coords to the frag shader

layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

void main() {
    // vertex position in clip space
    gl_Position = ubo.proj * ubo.view * ubo.model * vec4(inPosition, 1.0);

    // interpolated vertex color
    //vertexColor = inColor;

    // interpolated texture coordinates
    texCoords = inTexCoords;
}
