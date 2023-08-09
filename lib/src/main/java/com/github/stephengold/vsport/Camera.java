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

import com.jme3.math.FastMath;
import jme3utilities.Validate;
import jme3utilities.math.MyMath;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A viewpoint for 3-D rendering, including its eye location, look direction,
 * (vertical) field-of-view and near/far clipping planes.
 * <p>
 * Intended for a Y-up environment. When the camera's azimuth and up angle are
 * both zero, it looks in the +X direction.
 */
public class Camera {
    // *************************************************************************
    // fields

    /**
     * rightward angle of the X-Z component of the look direction relative to
     * the world +X axis (in radians)
     */
    private float azimuthRadians;
    /**
     * vertical field-of-view angle (between the bottom plane and the top plane,
     * in radians, &gt;0, &lt;PI)
     */
    private float fovy = MyMath.toRadians(45f);
    /**
     * angle of the look direction above the world X-Z plane (in radians)
     */
    private float upAngleRadians;
    /**
     * distance from the camera to the far clipping plane (in world units)
     */
    private float zFar = 1000f;
    /**
     * distance from the camera to the near clipping plane (in world units)
     */
    private float zNear = 1f;
    /**
     * eye location (in world coordinates)
     */
    final private Vector3f eyeLocation = new Vector3f(0f, 0f, 10f);
    /**
     * look direction (unit vector in world coordinates)
     */
    final private Vector3f lookDirection = new Vector3f(0f, 0f, -1f);
    /**
     * right direction (unit vector in world coordinates)
     */
    final private Vector3f rightDirection = new Vector3f(1f, 0f, 0f);
    /**
     * "up" direction (unit vector in world coordinates)
     */
    final private Vector3f upDirection = new Vector3f(0f, 1f, 0f);
    // *************************************************************************
    // constructors

    /**
     * Instantiate a camera.
     */
    public Camera() {
        updateDirectionVectors();
    }

    /**
     * Instantiate a camera with the specified initial position.
     *
     * @param initLocation the desired initial location (in world coordinates,
     * not null)
     * @param initAzimuthRadians the desired initial azimuth angle (in radians)
     * @param initUpAngleRadians the desired initial altitude angle (in radians)
     */
    public Camera(Vector3f initLocation, float initAzimuthRadians,
            float initUpAngleRadians) {
        eyeLocation.set(initLocation);

        this.azimuthRadians = initAzimuthRadians;
        this.upAngleRadians = initUpAngleRadians;
        updateDirectionVectors();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the azimuth/heading/yaw angle.
     *
     * @return the rightward angle (in radians)
     */
    public float azimuthAngle() {
        return azimuthRadians;
    }

    /**
     * Return the eye location.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in world coordinates (either
     * {@code storeResult} or a new vector)
     */
    public Vector3f copyLocation(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(eyeLocation);
        } else {
            return storeResult.set(eyeLocation);
        }
    }

    /**
     * Return the look direction.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a unit vector in world coordinates (either {@code storeResult} or
     * a new vector)
     */
    public Vector3f copyLookDirection(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(lookDirection);
        } else {
            return storeResult.set(lookDirection);
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
     * Return the look direction. This is a convenience method.
     *
     * @return a new unit vector in world coordinates
     */
    public Vector3f getDirection() {
        return copyLookDirection(null);
    }

    /**
     * Return the eye location. This is a convenience method.
     *
     * @return a new location vector in world coordinates
     */
    public Vector3f getLocation() {
        return copyLocation(null);
    }

    /**
     * Teleport the eye by the specified offset without changing its
     * orientation.
     *
     * @param offset the desired offset (in world coordinates, not null,
     * unaffected)
     */
    public void move(Vector3f offset) {
        eyeLocation.add(offset);
    }

    /**
     * Teleport the eye to {@code newLocation} and re-orient it to look at
     * {@code targetLocation}.
     *
     * @param eyeLocation the desired eye location (in world coordinates, not
     * null, unaffected)
     * @param targetLocation the location to look at (in world coordinates, not
     * null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Camera reposition(Vector3fc eyeLocation, Vector3fc targetLocation) {
        this.eyeLocation.set(eyeLocation);

        Vector3f offset = new Vector3f(targetLocation).sub(eyeLocation);
        setLookDirection(offset);

        return this;
    }

    /**
     * Return the right direction.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a unit vector in world coordinates (either {@code storeResult} or
     * a new vector)
     */
    public Vector3f rightDirection(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(rightDirection);
        } else {
            return storeResult.set(rightDirection);
        }
    }

    /**
     * Increase azimuth by {@code rightRadians} and increase the up angle by
     * {@code upRadians}. The magnitude of the resulting up angle is limited to
     * {@code maxUpAngleRadians}.
     *
     * @param rightRadians (in radians)
     * @param upRadians (in radians)
     * @param maxUpAngleRadians (in radians)
     */
    public void rotateLimited(
            float rightRadians, float upRadians, float maxUpAngleRadians) {
        this.azimuthRadians += rightRadians;
        this.azimuthRadians = MyMath.standardizeAngle(azimuthRadians);

        this.upAngleRadians += upRadians;
        if (upAngleRadians > maxUpAngleRadians) {
            this.upAngleRadians = maxUpAngleRadians;
        } else if (upAngleRadians < -maxUpAngleRadians) {
            this.upAngleRadians = -maxUpAngleRadians;
        }

        updateDirectionVectors();
    }

    /**
     * Alter the azimuth/heading/yaw angle.
     *
     * @param newAzimuthInRadians the desired rightward angle of the X-Z
     * component of the look direction relative to the +X axis (in radians)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setAzimuth(float newAzimuthInRadians) {
        this.azimuthRadians = newAzimuthInRadians;
        updateDirectionVectors();

        return this;
    }

    /**
     * Alter the azimuth/heading/yaw angle.
     *
     * @param newAzimuthInDegrees the desired rightward angle of the X-Z
     * component of the look direction relative to the +X axis (in degrees)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setAzimuthDegrees(float newAzimuthInDegrees) {
        setAzimuth(MyMath.toRadians(newAzimuthInDegrees));
        return this;
    }

    /**
     * Alter the vertical field-of-view angle.
     *
     * @param newFovy the desired angle (in radians, &gt;0, &lt;PI)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setFovy(float newFovy) {
        Validate.inRange(newFovy, "new fovy", Float.MIN_VALUE, FastMath.PI);
        this.fovy = newFovy;
        return this;
    }

    /**
     * Alter the vertical field-of-view angle.
     *
     * @param newFovyInDegrees the desired angle (in degrees, &gt;0, &lt;180)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setFovyDegrees(float newFovyInDegrees) {
        Validate.inRange(newFovyInDegrees, "new fovy", Float.MIN_VALUE, 180f);
        setFovy(MyMath.toRadians(newFovyInDegrees));
        return this;
    }

    /**
     * Teleport the eye to the specified location without changing its
     * orientation.
     *
     * @param newLocation the desired location (in world coordinates, not null,
     * unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setLocation(Vector3fc newLocation) {
        eyeLocation.set(newLocation);
        return this;
    }

    /**
     * Re-orient the camera to look in the specified direction.
     *
     * @param direction the desired direction (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setLookDirection(Vector3f direction) {
        this.azimuthRadians = FastMath.atan2(direction.z, direction.x);
        float nxz = MyMath.hypotenuse(direction.x, direction.z);
        this.upAngleRadians = FastMath.atan2(direction.y, nxz);
        updateDirectionVectors();

        return this;
    }

    /**
     * Alter the altitude/climb/elevation/pitch angle.
     *
     * @param newUpAngleInRadians the desired upward angle of the look direction
     * (in radians)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setUpAngle(float newUpAngleInRadians) {
        this.upAngleRadians = newUpAngleInRadians;
        updateDirectionVectors();

        return this;
    }

    /**
     * Alter the altitude/climb/elevation/pitch angle.
     *
     * @param newUpAngleInDegrees the desired upward angle of the look direction
     * (in degrees)
     * @return the (modified) current instance (for chaining)
     */
    public Camera setUpAngleDegrees(float newUpAngleInDegrees) {
        setUpAngle(MyMath.toRadians(newUpAngleInDegrees));
        return this;
    }

    /**
     * Alter both the near and far clipping planes.
     *
     * @param newZNear (&gt;0, &lt;zFar, default=1)
     * @param newZFar (&gt;zFar, default=1000)
     */
    public void setZClip(float newZNear, float newZFar) {
        Validate.inRange(newZNear, "new zNear", Float.MIN_VALUE, newZFar);

        this.zNear = newZNear;
        this.zFar = newZFar;
    }

    /**
     * Alter the distance from the camera to the far clipping plane.
     *
     * @param newZFar (in world units, &gt;zNear, default=1000)
     */
    public void setZFar(float newZFar) {
        Validate.inRange(newZFar, "new zFar", zNear, Float.MAX_VALUE);
        this.zFar = newZFar;
    }

    /**
     * Alter the distance from the camera to the near clipping plane.
     *
     * @param newZNear (in world units, &gt;0, &lt;zFar, default=1)
     */
    public void setZNear(float newZNear) {
        Validate.inRange(newZNear, "new zNear", Float.MIN_VALUE, zNear);
        this.zNear = newZNear;
    }

    /**
     * Return the altitude/climb/elevation/pitch angle.
     *
     * @return the upward angle of the look direction (in radians)
     */
    public float upAngle() {
        return upAngleRadians;
    }

    /**
     * Update the specified projection matrix.
     *
     * @param projMatrix the matrix to update (not null, modified)
     */
    void updateProjectionMatrix(Matrix4f projMatrix) {
        float aspectRatio = BaseApplication.aspectRatio();
        boolean zeroToOne = true;
        projMatrix.setPerspective(fovy, aspectRatio, zNear, zFar, zeroToOne);

        // In Vulkan's clip space, the Y axis increases downward, not upward:
        float m11 = projMatrix.m11();
        projMatrix.m11(-m11);
    }

    /**
     * Update the specified view matrix.
     *
     * @param viewMatrix the matrix to update (not null, modified)
     */
    void updateViewMatrix(Matrix4f viewMatrix) {
        Vector3f tmpTarget = new Vector3f(eyeLocation).add(lookDirection);
        viewMatrix.setLookAt(eyeLocation, tmpTarget, upDirection);
    }

    /**
     * Return the "up" direction.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a unit vector in world coordinates (either {@code storeResult} or
     * a new vector)
     */
    public Vector3f upDirection(Vector3f storeResult) {
        if (storeResult == null) {
            return new Vector3f(upDirection);
        } else {
            return storeResult.set(upDirection);
        }
    }

    /**
     * Return the distance from the camera to the far clipping plane.
     *
     * @return the distance (in world units, &gt;0)
     */
    public float zFar() {
        return zFar;
    }

    /**
     * Return the distance from the camera to the near clipping plane.
     *
     * @return the distance (in world units, &gt;0)
     */
    public float zNear() {
        return zNear;
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent the camera as a string of text.
     *
     * @return descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String result = String.format("loc[%g %g %g] az=%.2f upAng=%.2f"
                + " fovy=%.2f near=%.2f far=%g%n look[%.2f %.2f %.2f]"
                + " up[%.2f %.2f %.2f] right[%.2f %.2f %.2f]",
                eyeLocation.x(), eyeLocation.y(), eyeLocation.z(),
                azimuthRadians, upAngleRadians, fovy, zNear, zFar,
                lookDirection.x(), lookDirection.y(), lookDirection.z(),
                upDirection.x(), upDirection.y(), upDirection.z(),
                rightDirection.x(), rightDirection.y(), rightDirection.z()
        );
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Update the {@code lookDirection}, {@code rightDirection}, and
     * {@code upDirection} after changes to {@code azimuthRadians} and/or
     * {@code upAngleRadians}.
     */
    private void updateDirectionVectors() {
        float cosAzimuth = FastMath.cos(azimuthRadians);
        float sinAzimuth = FastMath.sin(azimuthRadians);
        float cosAltitude = FastMath.cos(upAngleRadians);
        float sinAltitude = FastMath.sin(upAngleRadians);

        float forwardX = cosAzimuth * cosAltitude;
        float forwardY = sinAltitude;
        float forwardZ = sinAzimuth * cosAltitude;
        lookDirection.set(forwardX, forwardY, forwardZ);

        float rightX = -sinAzimuth;
        float rightZ = cosAzimuth;
        rightDirection.set(rightX, 0f, rightZ);

        rightDirection.cross(lookDirection, upDirection);
    }
}