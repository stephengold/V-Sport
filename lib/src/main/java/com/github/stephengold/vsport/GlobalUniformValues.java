/*
 Copyright (c) 2023, Stephen Gold
 All rights reserved.

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

import java.nio.ByteBuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Shader parameters to be written to a global Uniform Buffer Objects (UBOs).
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the UniformBufferObject class in Cristian Herrera's
 * Vulkan-Tutorial-Java project.
 */
class GlobalUniformValues {
    // *************************************************************************
    // fields

    /**
     * strength of the ambient light
     */
    private float ambientStrength = 0.1f;
    /**
     * view-to-clip coordinate transform
     */
    final private Matrix4f projectionMatrix = new Matrix4f();
    /**
     * world-to-view coordinate transform
     */
    final private Matrix4f viewMatrix = new Matrix4f();
    /**
     * color of the lights
     */
    final private Vector3f lightColor = new Vector3f(1f, 1f, 1f);
    /**
     * direction to the directional light (in worldspace)
     */
    final private Vector3f lightDirectionWorldspace
            = new Vector3f(1f, 3f, 2f).normalize();
    // *************************************************************************
    // new methods exposed

    /**
     * Return the size of the UBO.
     *
     * @return the size (in bytes, &ge;0)
     */
    static int numBytes() {
        int result = 0;

        // float ambientStrength
        result += Float.BYTES;

        // vec3 LightDirection_worldspace
        result = Utils.align(result, 16);
        result += 3 * Float.BYTES;

        // vec3 LightColor
        result = Utils.align(result, 16);
        result += 3 * Float.BYTES;

        // mat4 viewMatrix
        result = Utils.align(result, 16);
        result += 4 * 4 * Float.BYTES;

        // mat4 projectionMatrix
        result = Utils.align(result, 16);
        result += 4 * 4 * Float.BYTES;

        return result;
    }

    /**
     * Alter the strength of the ambient light.
     *
     * @param newStrength the desired strength (default=0.1)
     */
    void setAmbientStrength(float newStrength) {
        this.ambientStrength = newStrength;
    }

    /**
     * Alter the color of lights.
     *
     * @param newColor the desired color (not null, unaffected, default=(1,1,1))
     */
    void setLightColor(Vector3fc newColor) {
        lightColor.set(newColor);
    }

    /**
     * Alter the direction to the directional light.
     *
     * @param newDirection the desired direction (in worldspace, not null, not
     * zero)
     */
    void setLightDirection(Vector3fc newDirection) {
        lightDirectionWorldspace.set(newDirection).normalize();
    }

    /**
     * Write the data to the specified ByteBuffer (starting at the current
     * buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeTo(ByteBuffer target) {
        // In Vulkan's clipspace, the Y axis increases downward, not upward:
        float m11 = projectionMatrix.m11();
        projectionMatrix.m11(-m11);

        int byteOffset = 0;

        // float ambientStrength
        target.putFloat(byteOffset, ambientStrength);
        byteOffset += Float.BYTES;

        // vec3 LightDirection_worldspace
        byteOffset = Utils.align(byteOffset, 16);
        lightDirectionWorldspace.get(byteOffset, target);
        byteOffset += 3 * Float.BYTES;

        // vec3 LightColor
        byteOffset = Utils.align(byteOffset, 16);
        lightColor.get(byteOffset, target);
        byteOffset += 3 * Float.BYTES;

        // mat4 viewMatrix
        byteOffset = Utils.align(byteOffset, 16);
        Camera camera = BaseApplication.getCamera();
        camera.updateViewMatrix(viewMatrix);
        viewMatrix.get(byteOffset, target);
        byteOffset += 4 * 4 * Float.BYTES;

        // mat4 projectionMatrix
        byteOffset = Utils.align(byteOffset, 16);
        Projection projection = BaseApplication.getProjection();
        projection.updateMatrix(projectionMatrix);
        projectionMatrix.get(byteOffset, target);
        byteOffset += 4 * 4 * Float.BYTES;

        assert byteOffset == numBytes() :
                "byteOffset=" + byteOffset + " numBytes = " + numBytes();
    }
}
