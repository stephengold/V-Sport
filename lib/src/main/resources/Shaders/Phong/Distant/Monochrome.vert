/*
 * vertex shader for the Phong/Distant/Monochrome program in V-Sport:
 *  Phong shading with a single distant light, alpha=8
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
layout(location = 1) in vec3 vertexNormal_modelspace; // normals from a vertex buffer

layout(location = 1) out vec3 EyeDirection_cameraspace;
layout(location = 2) out vec3 LightDirection_cameraspace;
layout(location = 3) out vec3 Normal_cameraspace;

void main() {
    // vertex position in camera space
    vec4 vertexPosition_cameraspace = global.viewMatrix * ubo.modelMatrix * vec4(vertexPosition_modelspace, 1);

    // vertex position in clip space
    gl_Position = global.projectionMatrix * vertexPosition_cameraspace;

    // direction from the vertex to the camera, in camera space
    // In camera space, the camera is at (0,0,0).
    EyeDirection_cameraspace = vec3(0,0,0) - vertexPosition_cameraspace.xyz;

    // direction from the vertex to the light, in camera space
    LightDirection_cameraspace = (global.viewMatrix * vec4(global.LightDirection_worldspace, 0)).xyz;

    // vertex normal in camera space
    Normal_cameraspace = (global.viewMatrix * vec4(ubo.modelRotationMatrix * vertexNormal_modelspace, 0)).xyz;
}