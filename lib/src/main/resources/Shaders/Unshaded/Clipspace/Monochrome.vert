/*
 * vertex shader for the Unshaded/Clipspace/Monochrome program in V-Sport
 */
#version 450

layout(binding = 1) uniform PerGeometry {
    vec4 BaseMaterialColor; // for ambient/diffuse lighting
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
} geometry;

layout(location = 0) in vec3 vertexPosition_modelspace;

void main() {
    // vertex position in clip space
    gl_Position = geometry.modelMatrix * vec4(vertexPosition_modelspace, 1.0);
}
