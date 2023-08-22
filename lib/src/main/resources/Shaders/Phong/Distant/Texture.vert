/*
 * vertex shader for the Phong/Distant/Texture program in V-Sport:
 *  Phong shading with a single distant light, alpha=8
 */
#version 450

layout(binding = 0) uniform Global { // global uniforms:
    float ambientStrength;
    vec3 LightDirection_worldspace;
    vec3 LightColor;
    mat4 viewMatrix;
    mat4 projectionMatrix;
} global;

layout(binding = 1) uniform PerGeometry {
    vec4 BaseMaterialColor;
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
    float alphaDiscardMaterialThreshold;
    float pointMaterialSize;
} geometry;

layout(location = 0) in vec3 vertexPosition_modelspace; // positions from a vertex buffer
layout(location = 1) in vec3 vertexNormal_modelspace; // normals from a vertex buffer
layout(location = 2) in vec2 vertexUV; // texture coordinates from a vertex buffer

layout(location = 1) out vec2 UV;
layout(location = 2) out vec3 EyeDirection_cameraspace;
layout(location = 3) out vec3 LightDirection_cameraspace;
layout(location = 4) out vec3 Normal_cameraspace;

void main() {
    // vertex position in cameraspace
    vec4 vertexPosition_cameraspace = global.viewMatrix * geometry.modelMatrix * vec4(vertexPosition_modelspace, 1);

    // vertex position in clipspace
    gl_Position = global.projectionMatrix * vertexPosition_cameraspace;

    // direction from the vertex to the camera, in cameraspace
    // In cameraspace, the camera is at (0,0,0).
    EyeDirection_cameraspace = vec3(0,0,0) - vertexPosition_cameraspace.xyz;

    // direction from the vertex to the light, in cameraspace
    LightDirection_cameraspace = (global.viewMatrix * vec4(global.LightDirection_worldspace, 0)).xyz;

    // vertex normal in cameraspace
    Normal_cameraspace = (global.viewMatrix * vec4(geometry.modelRotationMatrix * vertexNormal_modelspace, 0)).xyz;

    // texture coordinates of the vertex
    UV = vertexUV;
}
