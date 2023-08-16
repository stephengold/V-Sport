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
import jme3utilities.Validate;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/**
 * Shader parameters to be written to per-geometry Uniform Buffer Objects
 * (UBOs).
 *
 * @author Stephen Gold sgold@sonic.net
 */
class NonGlobalUniformValues {
    // *************************************************************************
    // fields

    /**
     * temporary storage (TODO not thread-safe)
     */
    final private static Matrix3f tmpMatrix3f = new Matrix3f();
    final private static Matrix4f tmpMatrix4f = new Matrix4f();
    /**
     * mesh-to-world rotation
     */
    final private Quaternionf orientation = new Quaternionf();
    /**
     * mesh-to-world offset (world location of the mesh origin)
     */
    final private Vector3f location = new Vector3f();
    /**
     * mesh-to-world scale factor for each mesh axis
     */
    final private Vector3f scale = new Vector3f(1f);
    /**
     * material color (in Linear colorspace) to use with ambient/diffuse
     * lighting
     */
    final private Vector4f baseMaterialColor = new Vector4f(1f);
    /**
     * material color (in Linear colorspace) to use in specular reflections
     */
    final private Vector4f specularMaterialColor = new Vector4f(1f);
    // *************************************************************************
    // new methods exposed

    /**
     * Return a copy of the mesh-to-world coordinate transform.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the transform (either {@code storeResult} or a new matrix, not
     * null)
     */
    Matrix4f copyTransform(Matrix4f storeResult) {
        Matrix4f result = (storeResult == null) ? new Matrix4f() : storeResult;
        /*
         * Conceptually, the order of application is: scale, then rotate, then
         * translate. However, matrices combine using post-multiplication ...
         */
        result.translation(location);
        result.rotate(orientation);
        result.scale(scale);

        return result;
    }

    /**
     * Return a copy of the location of the mesh origin.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in world coordinates (either
     * {@code storeResult} or a new vector, not null)
     */
    Vector3f location(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(location);
        } else {
            return storeResult.set(location);
        }
    }

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
        result += 11 * Float.BYTES;

        // vec4 SpecularMaterialColor
        result = Utils.align(result, 16);
        result += 4 * Float.BYTES;

        return result;
    }

    /**
     * Return a copy of the mesh-to-world coordinate rotation.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the rotation (either {@code storeResult} or a new quaternion, not
     * null)
     */
    Quaternionf orientation(Quaternionf storeResult) {
        if (storeResult == null) {
            return new Quaternionf(orientation);
        } else {
            return storeResult.set(orientation);
        }
    }

    /**
     * Reset the model transform so that mesh coordinates and world coordinates
     * are the same.
     */
    void resetModelTransform() {
        scale.set(1f);
        orientation.identity();
        location.zero();
    }

    /**
     * Apply the specified rotation, without shifting the local origin.
     *
     * @param rotation the rotation to apply (not null, each row is a unit
     * vector, unaffected)
     */
    void rotate(Matrix3fc rotation) {
        Matrix3fc transpose = new Matrix3f(rotation).transpose();
        Quaternionf q = new Quaternionf().setFromNormalized(transpose);
        orientation.premul(q);
    }

    /**
     * Rotate the model by the specified angle around the specified axis,
     * without shifting the mesh origin.
     * <p>
     * The rotation axis is assumed to be a unit vector.
     *
     * @param angle the rotation angle to apply (in radians, finite, 0&rarr;no
     * effect)
     * @param axisX the X component of the rotation axis (&ge;-1, &le;1)
     * @param axisY the Y component of the rotation axis (&ge;-1, &le;1)
     * @param axisZ the Z component of the rotation axis (&ge;-1, &le;1)
     */
    void rotateAngleAxis(float angle, float axisX, float axisY, float axisZ) {
        Quaternionf q = new Quaternionf();
        q.fromAxisAngleRad(axisX, axisY, axisZ, angle);
        orientation.premul(q);
    }

    /**
     * Uniformly scale the model by the specified factor.
     *
     * @param factor the scaling factor (1 = no effect)
     */
    void scale(float factor) {
        scale.mul(factor);
    }

    /**
     * Alter the base color.
     *
     * @param desiredColor the desired color (not null, unaffected)
     */
    void setBaseMaterialColor(Vector4fc desiredColor) {
        Validate.nonNull(desiredColor, "desired color");
        baseMaterialColor.set(desiredColor);
    }

    /**
     * Translate the mesh origin to the specified coordinates.
     *
     * @param x the desired world X coordinate (finite, default=0)
     * @param y the desired world Y coordinate (finite, default=0)
     * @param z the desired world Z coordinate (finite, default=0)
     */
    void setLocation(float x, float y, float z) {
        location.set(x, y, z);
    }

    /**
     * Translate the mesh origin.
     *
     * @param desiredLocation the desired location (in world coordinates, not
     * null, unaffected, default=(0,0,0))
     */
    void setLocation(Vector3fc desiredLocation) {
        Validate.nonNull(desiredLocation, "desired location");
        location.set(desiredLocation);
    }

    /**
     * Alter the mesh-to-world coordinate rotation.
     * <p>
     * The axis is assumed to be a unit vector.
     *
     * @param angle the desired rotation angle (in radians, default=0)
     * @param x the X component of the rotation axis
     * @param y the Y component of the rotation axis
     * @param z the Z component of the rotation axis
     */
    void setOrientation(float angle, float x, float y, float z) {
        orientation.fromAxisAngleRad(x, y, z, angle);
    }

    /**
     * Alter the orientation without shifting the mesh origin.
     *
     * @param desiredOrientation the desired orientation (not null, each row is
     * a unit vector, unaffected)
     */
    void setOrientation(Matrix3fc desiredOrientation) {
        Matrix3fc transpose = new Matrix3f(desiredOrientation).transpose();
        orientation.setFromNormalized(transpose);
    }

    /**
     * Alter the mesh-to-world scale factors.
     *
     * @param factor the desired mesh-to-world scale factor for all axes
     * (default=1)
     */
    void setScale(float factor) {
        scale.set(factor);
    }

    /**
     * Write the data to the specified ByteBuffer (starting at the current
     * buffer position) and advance the buffer position.
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
        copyTransform(tmpMatrix4f);
        tmpMatrix4f.get(byteOffset, target);
        byteOffset += 4 * 4 * Float.BYTES;

        // mat3 modelRotationMatrix
        tmpMatrix3f.rotation(orientation);
        byteOffset = Utils.getToBuffer(tmpMatrix3f, byteOffset, target);

        // vec4 SpecularMaterialColor
        byteOffset = Utils.align(byteOffset, 16);
        specularMaterialColor.get(byteOffset, target);
        byteOffset += 4 * Float.BYTES;

        assert byteOffset == numBytes() :
                "byteOffset=" + byteOffset + " numBytes=" + numBytes();
    }
}
