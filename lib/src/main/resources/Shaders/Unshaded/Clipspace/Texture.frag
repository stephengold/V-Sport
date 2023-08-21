/*
 * fragment shader for the Unshaded/Clipspace/Texture program in V-Sport
 */
#version 450

layout(binding = 2) uniform sampler2D ColorMaterialTexture;
layout(location = 1) in vec2 UV;
layout(location = 0) out vec3 fragColor;

void main() {
    fragColor = texture(ColorMaterialTexture, UV).rgb;
}
