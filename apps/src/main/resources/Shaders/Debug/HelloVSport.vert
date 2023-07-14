/*
 * vertex shader for the Debug/HelloVSport program
 */
#version 450

layout(location = 0) in vec2 inPosition;  // locations from vertex buffer
layout(location = 1) in vec3 inColor;     // colors from vertex buffer
layout(location = 2) in vec2 inTexCoords; // texture coords from vertex buffer

layout(location = 0) out vec3 vertexColor;  // colors to the frag shader
layout(location = 1) out vec2 texCoords;    // texture coords to the frag shader

layout(binding = 0) uniform UniformBufferObject {
    mat4 model;
    mat4 view;
    mat4 proj;
} ubo;

void main() {
    // vertex position in clip space
    gl_Position = ubo.proj * ubo.view * ubo.model * vec4(inPosition, 0.0, 1.0);

    // vertex color
    vertexColor = inColor;

    // interpolated texture coordinates
    texCoords = inTexCoords;
}
