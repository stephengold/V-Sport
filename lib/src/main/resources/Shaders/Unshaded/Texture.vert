/*
 * vertex shader for the Unshaded/Texture program in V-Sport
 */
#version 450

layout(binding = 0) uniform Global { // global uniforms:
    float ambientStrength;
    vec3 LightDirection_worldspace;
    vec4 LightColor;
    mat4 viewMatrix;
    mat4 projectionMatrix;
} global;

layout(binding = 1) uniform NonGlobal {
    vec4 BaseMaterialColor; // for ambient/diffuse lighting
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
} ubo;

layout(location = 0) in vec3 vertexPosition_modelspace; // positions from a vertex buffer
layout(location = 1) in vec2 vertexUV; // texture coordinates from a vertex buffer

layout(location = 1) out vec2 UV; // texture coordinates to the frag shader

void main() {
    // vertex position in clip space
    gl_Position = global.projectionMatrix * global.viewMatrix * ubo.modelMatrix
                * vec4(vertexPosition_modelspace, 1.0);

    // texture coordinates of the vertex
    UV = vertexUV;
}
