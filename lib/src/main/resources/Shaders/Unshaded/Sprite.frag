/*
 * fragment shader for the Unshaded/Sprite program in V-Sport
 * The alpha discard threshold and point size are set on a per-geometry basis.
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
layout(binding = 2) uniform sampler2D ColorMaterialTexture;

layout(location = 0) out vec3 fragColor;

void main() {
    vec4 sampleColor = texture(ColorMaterialTexture, gl_PointCoord);
    vec4 color = geometry.BaseMaterialColor * sampleColor;
    if (color.a < geometry.alphaDiscardMaterialThreshold) {
        discard;
    }
    fragColor = color.rgb;
}
