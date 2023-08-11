/*
 * vertex shader for the Unshaded/Monochrome program in V-Sport
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

layout(location = 0) in vec3 vertexPosition_modelspace;

void main() {
    // vertex position in clip space
    gl_Position = global.projectionMatrix * global.viewMatrix * ubo.modelMatrix * vec4(vertexPosition_modelspace, 1.0);
}
