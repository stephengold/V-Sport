/*
 * fragment shader for the Debug/LocalNormals program in V-Sport
 */
#version 450

layout(location = 1) in vec3 Normal_modelspace;
layout(location = 0) out vec3 fragColor;

void main() {
    vec3 srgb = (Normal_modelspace * vec3(0.5)) + vec3(0.5);
    fragColor = pow(srgb, vec3(2.2, 2.2, 2.2));
}
