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
import org.joml.Vector4f;

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
     * Return a copy of the mesh-to-world scale factors.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a vector of scale factors (either {@code storeResult} or a new
     * instance, not null)
     */
    Vector3f copyScale(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(scale);
        } else {
            return storeResult.set(scale);
        }
    }

    /**
     * Return a copy of the mesh-to-world scale factors.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a vector of scale factors (either {@code storeResult} or a new
     * instance, not null)
     */
    com.jme3.math.Vector3f copyScaleJme(
            com.jme3.math.Vector3f storeResult) {
        if (storeResult == null) {
            return new com.jme3.math.Vector3f(scale.x(), scale.y(), scale.z());
        } else {
            return storeResult.set(scale.x(), scale.y(), scale.z());
        }
    }

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
     * Return a copy of the location of the mesh origin.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in world coordinates (either
     * {@code storeResult} or a new vector, not null)
     */
    com.jme3.math.Vector3f locationJme(
            com.jme3.math.Vector3f storeResult) {
        if (storeResult == null) {
            return new com.jme3.math.Vector3f(
                    location.x(), location.y(), location.z());
        } else {
            return storeResult.set(location.x(), location.y(), location.z());
        }
    }

    /**
     * Translate the model by the specified offsets without changing its
     * orientation.
     *
     * @param xOffset the offset in the world +X direction (finite, 0&rarr;no
     * effect)
     * @param yOffset the offset in the world +Y direction (finite, 0&rarr;no
     * effect)
     * @param zOffset the offset in the world +Z direction (finite, 0&rarr;no
     * effect)
     */
    void move(float xOffset, float yOffset, float zOffset) {
        Validate.finite(xOffset, "x offset");
        Validate.finite(yOffset, "y offset");
        Validate.finite(zOffset, "z offset");

        location.add(xOffset, yOffset, zOffset);
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
     * @return a unit quaternion (either {@code storeResult} or a new
     * quaternion)
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
     * Apply the specified rotation, without shifting the mesh origin.
     *
     * @param rotation the rotation to apply (not null, each row is a unit
     * vector, unaffected)
     */
    void rotate(Matrix3fc rotation) {
        Validate.nonNull(rotation, "rotation");

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
        Validate.finite(angle, "angle");
        Validate.inRange(axisX, "axis x", -1f, 1f);
        Validate.inRange(axisY, "axis y", -1f, 1f);
        Validate.inRange(axisZ, "axis z", -1f, 1f);

        Quaternionf q = new Quaternionf();
        q.fromAxisAngleRad(axisX, axisY, axisZ, angle);
        orientation.premul(q);
    }

    /**
     * Scale the model by the specified factors.
     *
     * @param xFactor the scale factor to apply to the mesh X axis (finite,
     * &ge;0, 1&rarr;no effect)
     * @param yFactor the scale factor to apply to the mesh Y axis (finite,
     * &ge;0, 1&rarr;no effect)
     * @param zFactor the scale factor to apply to the mesh Z axis (finite,
     * &ge;0, 1&rarr;no effect)
     */
    void scale(float xFactor, float yFactor, float zFactor) {
        Validate.finite(xFactor, "x factor");
        Validate.finite(yFactor, "y factor");
        Validate.finite(zFactor, "z factor");
        Validate.nonNegative(xFactor, "x factor");
        Validate.nonNegative(yFactor, "y factor");
        Validate.nonNegative(zFactor, "z factor");

        scale.mul(xFactor, yFactor, zFactor);
    }

    /**
     * Alter the base color.
     *
     * @param red the desired red component (in Linear colorspace, &ge;0, &le;1,
     * default=1)
     * @param green the desired green component (in Linear colorspace, &ge;0,
     * &le;1, default=1)
     * @param blue the desired blue component (in Linear colorspace, &ge;0,
     * &le;1, default=1)
     * @param alpha the desired alpha component (&ge;0, default=1)
     */
    void setColor(float red, float green, float blue, float alpha) {
        Validate.fraction(red, "red");
        Validate.fraction(green, "green");
        Validate.fraction(blue, "blue");
        Validate.fraction(alpha, "alpha");

        baseMaterialColor.set(red, green, blue, alpha);
    }

    /**
     * Translate the mesh origin to the specified coordinates.
     *
     * @param x the desired world X coordinate (finite, default=0)
     * @param y the desired world Y coordinate (finite, default=0)
     * @param z the desired world Z coordinate (finite, default=0)
     */
    void setLocation(float x, float y, float z) {
        Validate.finite(x, "x");
        Validate.finite(y, "y");
        Validate.finite(z, "z");

        location.set(x, y, z);
    }

    /**
     * Alter the orientation using Tait-Bryan angles and applying the rotations
     * in x-z-y extrinsic order or y-z'-x" intrinsic order.
     *
     * @param xAngle the desired X-axis rotation angle (in radians, finite)
     * @param yAngle the desired Y-axis rotation angle (in radians, finite)
     * @param zAngle the desired Z-axis rotation angle (in radians, finite)
     */
    void setOrientation(float xAngle, float yAngle, float zAngle) {
        Validate.finite(xAngle, "x angle");
        Validate.finite(yAngle, "y angle");
        Validate.finite(zAngle, "z angle");

        orientation.rotationY(yAngle);
        orientation.rotateZ(zAngle);
        orientation.rotateX(xAngle);
    }

    /**
     * Alter the orientation without shifting the mesh origin.
     *
     * @param desiredOrientation the desired orientation (not null, each row is
     * a unit vector, unaffected)
     */
    void setOrientation(Matrix3fc desiredOrientation) {
        Validate.nonNull(desiredOrientation, "desired orientation");

        Matrix3fc transpose = new Matrix3f(desiredOrientation).transpose();
        orientation.setFromNormalized(transpose);
    }

    /**
     * Alter the orientation without shifting the mesh origin.
     * <p>
     * The rotation axis is assumed to be a unit vector.
     *
     * @param angle the desired rotation angle (in radians, finite, default=0)
     * @param axisX the X component of the rotation axis (&ge;-1, &le;1)
     * @param axisY the Y component of the rotation axis (&ge;-1, &le;1)
     * @param axisZ the Z component of the rotation axis (&ge;-1, &le;1)
     */
    void setOrientationAngleAxis(
            float angle, float axisX, float axisY, float axisZ) {
        Validate.finite(angle, "angle");
        Validate.inRange(axisX, "axis x", -1f, 1f);
        Validate.inRange(axisY, "axis y", -1f, 1f);
        Validate.inRange(axisZ, "axis z", -1f, 1f);

        orientation.fromAxisAngleRad(axisX, axisY, axisZ, angle);
    }

    /**
     * Alter the orientation, without shifting the mesh origin.
     * <p>
     * The quaternion is assumed to be a unit quaternion.
     *
     * @param x the desired X component of the quaternion (&ge;-1, &le;1,
     * default=0)
     * @param y the desired Y component of the quaternion (&ge;-1, &le;1,
     * default=0)
     * @param z the desired Z component of the quaternion (&ge;-1, &le;1,
     * default=0)
     * @param w the desired W (real) component of the quaternion (&ge;-1, &le;1,
     * default=1)
     */
    void setOrientationQuaternion(float x, float y, float z, float w) {
        Validate.inRange(x, "x", -1f, 1f);
        Validate.inRange(y, "y", -1f, 1f);
        Validate.inRange(z, "z", -1f, 1f);
        Validate.inRange(w, "w", -1f, 1f);

        orientation.set(x, y, z, w);
    }

    /**
     * Alter the mesh-to-world scale factors.
     *
     * @param xFactor the desired scale factor for the mesh X axis (finite,
     * &ge;0, default=1)
     * @param yFactor the desired scale factor for the mesh Y axis (finite,
     * &ge;0, default=1)
     * @param zFactor the desired scale factor for the mesh Z axis (finite,
     * &ge;0, default=1)
     */
    void setScale(float xFactor, float yFactor, float zFactor) {
        Validate.finite(xFactor, "x factor");
        Validate.finite(yFactor, "y factor");
        Validate.finite(zFactor, "z factor");
        Validate.nonNegative(xFactor, "x factor");
        Validate.nonNegative(yFactor, "y factor");
        Validate.nonNegative(zFactor, "z factor");

        scale.set(xFactor, yFactor, zFactor);
    }

    /**
     * Alter the specular color.
     *
     * @param red the desired red component (in Linear colorspace, &ge;0, &le;1,
     * default=1)
     * @param green the desired green component (in Linear colorspace, &ge;0,
     * &le;1, default=1)
     * @param blue the desired blue component (in Linear colorspace, &ge;0,
     * &le;1, default=1)
     * @param alpha the desired alpha component (&ge;0, default=1)
     */
    void setSpecularColor(float red, float green, float blue, float alpha) {
        Validate.fraction(red, "red");
        Validate.fraction(green, "green");
        Validate.fraction(blue, "blue");
        Validate.fraction(alpha, "alpha");

        specularMaterialColor.set(red, green, blue, alpha);
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
