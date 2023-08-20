/*
 Copyright (c) 2022-2023, Stephen Gold
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

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import jme3utilities.Validate;
import jme3utilities.math.MyMath;
import org.joml.Matrix4f;
import org.joml.Vector2fc;
import org.joml.Vector4f;

/**
 * The camera-to-clip transform for use in shaders.
 * <p>
 * In cameraspace:
 * <ul>
 * <li>coordinates are measured in world units from the eye location</li>
 * <li>right is +X</li>
 * <li>the camera's "up" direction is +Y</li>
 * <li>the camera's look direction is -Z</li>
 * </ul>
 * <p>
 * In clipspace:
 * <ul>
 * <li>the top clipping plane is Y=-1</li>
 * <li>the bottom clipping plane is Y=+1</li>
 * <li>the left clipping plane is X=-1</li>
 * <li>the right clipping plane is X=+1</li>
 * <li>the near clipping plane is Z=0</li>
 * <li>the far clipping plane is Z=+1</li>
 * </ul>
 *
 * Note that both cameraspace and clipspace are right-handed coordinate systems.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class Projection {
    // *************************************************************************
    // constants

    /**
     * Z coordinate of the far plane in clip space
     */
    final public static float farClipZ = 1f;
    /**
     * Z coordinate of the near plane in clip space
     */
    final public static float nearClipZ = 0f;
    // *************************************************************************
    // fields

    /**
     * vertical field-of-view angle (between the bottom plane and the top plane,
     * in radians, &gt;0, &lt;PI)
     */
    private float fovy = MyMath.toRadians(45f);
    /**
     * distance of the far clipping plane from the eye (in world units)
     */
    private float zFar = 1000f;
    /**
     * distance of the near clipping plane from the eye (in world units)
     */
    private float zNear = 1f;
    /**
     * temporary camera-to-clip transform matrix
     */
    final private Matrix4f tmpMatrix = new Matrix4f();
    // *************************************************************************
    // constructors

    /**
     * Instantiate a projection with the specified distances.
     *
     * @param zNear the desired distance of the near clipping plane (in world
     * units, &gt;0, &lt;zFar)
     * @param zFar the desired distance of the far clipping plane (in world
     * units; &gt;zNear) (&gt;zNear)
     */
    Projection(float zNear, float zFar) {
        this.zNear = zNear;
        this.zFar = zFar;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the specified camera-space coordinates to clip-space coordinates.
     *
     * @param location the camera-space coordinates to convert (not null,
     * unaffected)
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in clip space (either {@code storeResult} or a
     * new vector)
     */
    public Vector3f cameraToClip(Vector3f location, Vector3f storeResult) {
        updateMatrix(tmpMatrix);

        Vector4f hom = new Vector4f(location.x, location.y, location.z, 1f);
        hom.mul(tmpMatrix);
        hom.div(hom.w);

        if (storeResult == null) {
            return new Vector3f(hom.x, hom.y, hom.z);
        } else {
            return storeResult.set(hom.x, hom.y, hom.z);
        }
    }

    /**
     * Convert the specified clip-space coordinates to camera-space coordinates.
     *
     * @param clipXy the clip-space X and Y coordinates (not null, unaffected)
     * @param clipZ the clip-space Z coordinate (0 for near plane, +1 for far
     * plane)
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in camera space (either {@code storeResult} or
     * a new vector)
     */
    public Vector3f clipToCamera(
            Vector2fc clipXy, float clipZ, Vector3f storeResult) {
        // invert the camera-to-clip transform matrix
        updateMatrix(tmpMatrix);
        Matrix4f inverse = new Matrix4f();
        tmpMatrix.invert(inverse);

        Vector4f hom = new Vector4f(clipXy.x(), clipXy.y(), clipZ, 1f);
        hom.mul(inverse);
        hom.div(hom.w);

        if (storeResult == null) {
            return new Vector3f(hom.x, hom.y, hom.z);
        } else {
            return storeResult.set(hom.x, hom.y, hom.z);
        }
    }

    /**
     * Return the vertical field-of-view angle.
     *
     * @return the angle (in radians, &gt;0, &lt;PI)
     */
    public float fovy() {
        assert fovy > 0f : fovy;
        assert fovy < FastMath.PI : fovy;
        return fovy;
    }

    /**
     * Alter the vertical field-of-view angle.
     *
     * @param fovyRadians the desired angle (in radians, &gt;0, &lt;PI)
     * @return the (modified) current instance (for chaining)
     */
    public Projection setFovy(float fovyRadians) {
        Validate.inRange(
                fovyRadians, "fovy in radians", Float.MIN_VALUE, FastMath.PI);
        this.fovy = fovyRadians;
        return this;
    }

    /**
     * Alter the vertical field-of-view angle.
     *
     * @param fovyDegrees the desired angle (in degrees, &gt;0, &lt;180)
     * @return the (modified) current instance (for chaining)
     */
    public Projection setFovyDegrees(float fovyDegrees) {
        Validate.inRange(
                fovyDegrees, "fovy in degrees", Float.MIN_VALUE, 180f);
        setFovy(MyMath.toRadians(fovyDegrees));
        return this;
    }

    /**
     * Alter the distances of both the near and far clipping planes.
     *
     * @param zNear the desired near-plane distance (&gt;0, &lt;zFar, default=1)
     * @param zFar the desired far-plane distance (&gt;zNear, default=1000)
     * @return the (modified) current instance (for chaining)
     */
    public Projection setZClip(float zNear, float zFar) {
        Validate.inRange(zNear, "zNear", Float.MIN_VALUE, zFar);

        this.zNear = zNear;
        this.zFar = zFar;
        return this;
    }

    /**
     * Alter the distance of the far clipping plane.
     *
     * @param zFar the desired distance (in world units, &gt;zNear,
     * default=1000)
     * @return the (modified) current instance (for chaining)
     */
    public Projection setZFar(float zFar) {
        Validate.inRange(zFar, "zFar", zNear, Float.MAX_VALUE);
        this.zFar = zFar;
        return this;
    }

    /**
     * Alter the distance of the near clipping plane.
     *
     * @param zNear the desired distance (in world units, &gt;0, &lt;zFar,
     * default=1)
     * @return the (modified) current instance (for chaining)
     */
    public Projection setZNear(float zNear) {
        Validate.inRange(zNear, "zNear", Float.MIN_VALUE, zNear);
        this.zNear = zNear;
        return this;
    }

    /**
     * Return the distance of the far clipping plane.
     *
     * @return the distance (in world units, &gt;zNear)
     */
    public float zFar() {
        assert zNear < zFar : "zNear = " + zNear + ", zFar = " + zFar;
        return zFar;
    }

    /**
     * Return the distance of the near clipping plane.
     *
     * @return the distance (in world units, &gt;0, &lt;zFar)
     */
    public float zNear() {
        assert zNear > 0f : zNear;
        assert zNear < zFar : "zNear = " + zNear + ", zFar = " + zFar;
        return zNear;
    }

    /**
     * Store the projection matrix (camera-to-clip transform) in the specified
     * matrix.
     *
     * @param storeMatrix the matrix to update (not null, modified)
     */
    void updateMatrix(Matrix4f storeMatrix) {
        float aspectRatio = BaseApplication.aspectRatio();
        boolean zeroToOne = true;
        storeMatrix.setPerspective(
                fovy, aspectRatio, zNear, zFar, zeroToOne);

        // In Vulkan's clip space, the Y axis increases downward, not upward:
        float m11 = storeMatrix.m11();
        storeMatrix.m11(-m11);
    }
    // *************************************************************************
    // Object methods

    /**
     * Describe the projection in a string of text.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String result = String.format(
                "fovy=%.2f near=%.2f far=%g", fovy, zNear, zFar);
        return result;
    }
}
