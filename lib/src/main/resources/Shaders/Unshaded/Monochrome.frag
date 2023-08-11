/*
 * fragment shader for the Unshaded/Monochrome program in V-Sport
 */
#version 450

layout(binding = 1) uniform NonGlobal {
    vec4 BaseMaterialColor; // for ambient/diffuse lighting
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
} ubo;

layout(location = 0) out vec3 fragColor;

void main() {
    fragColor = ubo.BaseMaterialColor.rgb;
}
