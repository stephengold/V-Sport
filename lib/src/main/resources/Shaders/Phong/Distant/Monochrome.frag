/*
 * fragment shader for the Phong/Distant/Monochrome program in V-Sport:
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
    vec4 BaseMaterialColor; // for ambient/diffuse lighting
    mat4 modelMatrix;
    mat3 modelRotationMatrix;
    vec4 SpecularMaterialColor;
    float alphaDiscardMaterialThreshold;
    float pointMaterialSize;
} geometry;

layout(location = 1) in vec3 EyeDirection_cameraspace;
layout(location = 2) in vec3 LightDirection_cameraspace;
layout(location = 3) in vec3 Normal_cameraspace;

layout(location = 0) out vec3 fragColor;

void main() {
    // normal of the fragment, in worldspace
    vec3 N = normalize(Normal_cameraspace);

    // direction from the fragment to the light, in cameraspace
    vec3 L = normalize(LightDirection_cameraspace);

    // cosine of the angle between the normal and the light direction,
    // clamped above 0
    //  - light is at the vertical of the triangle -> 1
    //  - light is perpendicular to the triangle -> 0
    //  - light is behind the triangle -> 0
    float cosTheta = clamp(dot(N, L), 0, 1);

    // eye vector (towards the camera)
    vec3 E = normalize(EyeDirection_cameraspace);

    // direction in which the triangle reflects the light
    vec3 R = reflect(-L, N);

    // cosine of the angle between the Eye vector and the Reflect vector,
    // clamped to 0
    //  - looking at the reflection: 1
    //  - looking elsewhere: < 1
    float cosAlpha = clamp(dot(E, R), 0, 1);
    float cosAlpha2 = cosAlpha * cosAlpha;
    float cosAlpha4 = cosAlpha2 * cosAlpha2;
    float cosAlpha8 = cosAlpha4 * cosAlpha4;

    vec3 color = (global.ambientStrength + cosTheta) * geometry.BaseMaterialColor.rgb;
    color = color + cosAlpha8 * geometry.SpecularMaterialColor.rgb;
    fragColor = color * global.LightColor;
}
