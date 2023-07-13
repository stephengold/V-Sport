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
 * Values to be written to a uniform buffer object: 3 transform matrices.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the UniformBufferObject class in Cristian Herrera's
 * Vulkan-Tutorial-Java project.
 */
class UniformValues {
    // *************************************************************************
    // fields

    /**
     * model-to-world coordinate transform
     */
    final private Matrix4f model = new Matrix4f();
    /**
     * view-to-clip coordinate transform
     */
    final private Matrix4f proj = new Matrix4f();
    /**
     * world-to-view coordinate transform
     */
    final private Matrix4f view = new Matrix4f();
    // *************************************************************************
    // constructors

    /**
     * Instantiate a sample value set.
     */
    UniformValues() {
        Vector3fc eye = new Vector3f(2f, 2f, 2f);
        Vector3fc origin = new Vector3f(0f, 0f, 0f);
        Vector3fc up = new Vector3f(0f, 0f, 1f);  // +Z axis
        view.lookAt(eye, origin, up);

        double fov = Math.toRadians(45.); // in radians
        float aspectRatio = BaseApplication.aspectRatio();
        float zNear = 0.1f;
        float zFar = 10f;
        proj.perspective((float) fov, aspectRatio, zNear, zFar);

        // In Vulkan's clip space, the Y axis increases downward, not upward:
        float m11 = proj.m11();
        proj.m11(-m11);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the size of the UBO.
     *
     * @return the size (in bytes, &ge;0)
     */
    static int numBytes() {
        int result = 3 * 16 * Float.BYTES;
        return result;
    }

    /**
     * Alter the orientation of the model.
     *
     * @param angle the desired z-axis rotation
     */
    void setZRotation(double angle) {
        model.identity();
        model.rotate((float) angle, 0f, 0f, 1f);
    }

    /**
     * Write the uniform data to the specified ByteBuffer (starting at the
     * current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeTo(ByteBuffer target) {
        int mat4Bytes = 16 * Float.BYTES;

        model.get(0, target);
        view.get(mat4Bytes, target);
        proj.get(2 * mat4Bytes, target);
    }
}
