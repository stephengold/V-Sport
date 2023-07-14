/*
 * fragment shader for the Debug/HelloVSport program
 */
#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding = 1) uniform sampler2D texSampler;

//layout(location = 0) in vec3 vertexColor;
layout(location = 1) in vec2 texCoords;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(texSampler, texCoords);
}
