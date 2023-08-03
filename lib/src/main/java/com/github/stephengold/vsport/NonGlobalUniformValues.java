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
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Values to be written to a per-geometry uniform buffer object (UBO).
 *
 * @author Stephen Gold sgold@sonic.net
 */
class NonGlobalUniformValues {
    // *************************************************************************
    // fields

    /**
     * mesh-to-world coordinate rotation
     */
    final private Matrix3f modelRotationMatrix = new Matrix3f();
    /**
     * mesh-to-world coordinate transform
     */
    final private Matrix4f modelMatrix = new Matrix4f();
    /**
     * material color to use with ambient/diffuse lighting
     */
    final private Vector4f baseMaterialColor = new Vector4f();
    /**
     * material color to use with specular reflections
     */
    final private Vector4f specularMaterialColor = new Vector4f();
    // *************************************************************************
    // new methods exposed

    /**
     * Return the size of the UBO.
     *
     * @return the size (in bytes, &ge;0)
     */
    static int numBytes() {
        int result = 0;

        // vec4 BaseMaterialColor
        result += 4 * Float.BYTES;

        // mat4 modelMatrix
        result = Utils.align(result, 16);
        result += 4 * 4 * Float.BYTES;

        // mat3 modelRotationMatrix
        result = Utils.align(result, 16);
        result += 3 * 3 * Float.BYTES;

        // vec4 SpecularMaterialColor
        result += 4 * Float.BYTES;
        return result;
    }

    /**
     * Write the uniform data to the specified ByteBuffer (starting at the
     * current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeTo(ByteBuffer target) {
        int byteOffset = 0;

        // vec4 BaseMaterialColor
        baseMaterialColor.get(byteOffset, target);
        byteOffset += 4 * Float.BYTES;

        // mat4 modelMatrix
        byteOffset = Utils.align(byteOffset, 16);
        modelMatrix.get(byteOffset, target);
        byteOffset += 4 * 4 * Float.BYTES;

        // mat3 modelRotationMatrix
        byteOffset = Utils.align(byteOffset, 16);
        modelRotationMatrix.get(byteOffset, target);
        byteOffset += 3 * 3 * Float.BYTES;

        // vec4 SpecularMaterialColor
        specularMaterialColor.get(byteOffset, target);
        byteOffset += 4 * Float.BYTES;

        assert byteOffset == numBytes() :
                "byteOffset=" + byteOffset + " numBytes = " + numBytes();
    }
}
