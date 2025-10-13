/*
 Copyright (c) 2023-2025 Stephen Gold

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.vsport;

/**
 * Enumerate the inputs to a ShaderProgram, including both uniforms and vertex
 * attributes.
 *
 * @author Stephen Gold sgold@sonic.net
 */
enum ShaderInput {
    // *************************************************************************
    // values

    /**
     * alpha discard threshold for sprites (float in the per-geometry uniform)
     */
    AlphaDiscardThreshold(false, "geometry.alphaDiscardMaterialThreshold"),
    /**
     * strength of the ambient light (float in the global uniform)
     */
    AmbientStrength(false, "global.ambientStrength"),
    /**
     * light color for ambient/diffuse lighting (in linear colorspace, vec3 in
     * the global uniform)
     */
    LightColor(false, "global.LightColor"),
    /**
     * direction to the distant light in worldspace (vec3 in the global uniform)
     */
    LightDirection(false, "global.LightDirection_worldspace"),
    /**
     * material color for ambient/diffuse lighting or sprites (in linear
     * colorspace, vec4 in the per-geometry uniform)
     */
    MaterialColor(false, "geometry.BaseMaterialColor"),
    /**
     * point size for sprites (float in the per-geometry uniform)
     */
    MaterialPointSize(false, "geometry.pointMaterialSize"),
    /**
     * base material texture for U-V sampling (sampler2D at uniform binding=2)
     */
    MaterialTexture(false, "ColorMaterialTexture"),
    /**
     * mesh-to-world transform (mat4 in the per-geometry uniform)
     * <p>
     * When using cameraspace shaders, this becomes the mesh-to-camera
     * transform.
     * <p>
     * When using clipspace shaders, this becomes the mesh-to-clip transform.
     */
    ModelMatrix(false, "geometry.modelMatrix"),
    /**
     * mesh-to-world rotation (mat3 in the per-geometry uniform)
     * <p>
     * When using cameraspace shaders, this becomes the mesh-to-camera rotation.
     */
    ModelRotationMatrix(false, "geometry.modelRotationMatrix"),
    /**
     * camera-to-clip transform matrix (mat4 in the global uniform)
     */
    ProjectionMatrix(false, "global.projectionMatrix"),
    /**
     * material color for specular lighting in linear colorspace (vec4 in the
     * per-geometry uniform)
     */
    SpecularColor(false, "geometry.SpecularMaterialColor"),
    /**
     * color of each vertex in linear colorspace (vec3 in vertex buffer)
     */
    VertexColor(true, "vertexColor"),
    /**
     * normal direction of each vertex in mesh space (vec3 from a vertex buffer)
     */
    VertexNormal(true, "vertexNormal_modelspace"),
    /**
     * position (location) of each vertex in mesh space (vec3 from a vertex
     * buffer)
     */
    VertexPosition(true, "vertexPosition_modelspace"),
    /**
     * UV coordinates of each vertex in texture space (vec2 from a vertex
     * buffer)
     */
    VertexTexCoords(true, "vertexUV"),
    /**
     * world-to-camera transform (mat4 in the global uniform)
     */
    ViewMatrix(false, "global.viewMatrix");
    // *************************************************************************
    // fields

    /**
     * {@code true} if the input is a vertex attribute, otherwise {@code false}
     */
    final private boolean isVertexAttribute;
    /**
     * name used in frag/txt/vert files (not null, not empty)
     */
    final private String variableName;
    // *************************************************************************
    // constructors

    /**
     * Construct an enum value.
     *
     * @param isVertexAttribute true if a vertex attribute, otherwise false
     * @param variableName the name used in frag/txt/vert files (not null, not
     * empty)
     */
    ShaderInput(boolean isVertexAttribute, String variableName) {
        this.isVertexAttribute = isVertexAttribute;
        this.variableName = variableName;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Test whether the input is a vertex attribute.
     *
     * @return true if it's a vertex attribute, otherwise false
     */
    boolean isVertexAttribute() {
        return isVertexAttribute;
    }

    /**
     * Find the value for the specified variable name.
     *
     * @param name the variable name to search for
     * @return the enum value with that name, or null if not found
     */
    static ShaderInput find(String name) {
        for (ShaderInput value : values()) {
            if (value.variableName.equals(name)) {
                return value;
            }
        }
        return null;
    }
}
