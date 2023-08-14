/*
 * vertex shader for the Debug/WorldNormals program in V-Sport
 */
#version 450

layout(binding = 0) uniform Global { // global uniforms:
    float ambientStrength;
    vec3 LightDirection_worldspace;
    vec3 LightColor;
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
layout(location = 1) in vec3 vertexNormal_modelspace; // normals from a vertex buffer

layout(location = 1) out vec3 Normal_worldspace; // normals to the frag shader

void main() {
    // vertex position in clip space
    gl_Position = global.projectionMatrix * global.viewMatrix * ubo.modelMatrix * vec4(vertexPosition_modelspace, 1.0);

    // vertex normal in world space
    Normal_worldspace = ubo.modelRotationMatrix * vertexNormal_modelspace;
}