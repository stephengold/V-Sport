/*
 * vertex shader for the Unshaded/Clipspace/Texture program in V-Sport
 */
#version 450

layout(binding = 1) uniform PerGeometry {
    vec4 BaseMaterialColor;
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
    float alphaDiscardMaterialThreshold;
    float pointMaterialSize;
} geometry;

layout(location = 0) in vec3 vertexPosition_modelspace; // positions from a vertex buffer
layout(location = 1) in vec2 vertexUV; // texture coordinates from a vertex buffer

layout(location = 1) out vec2 UV; // texture coordinates to the frag shader

void main() {
    // vertex position in clipspace
    gl_Position = geometry.modelMatrix * vec4(vertexPosition_modelspace, 1.0);

    // texture coordinates of the vertex
    UV = vertexUV;
}
