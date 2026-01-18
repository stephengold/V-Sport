/*
 * vertex shader for the Unshaded/Sprite program in V-Sport
 * The alpha discard threshold and point size are set on a per-geometry basis.
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

void main() {
    // vertex point size, in pixels:
    gl_PointSize = geometry.pointMaterialSize;

    // vertex position in clipspace:
    gl_Position = global.projectionMatrix * global.viewMatrix * geometry.modelMatrix
                * vec4(vertexPosition_modelspace, 1.0);
}
