/*
 Copyright (c) 2023, Stephen Gold

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

import com.jme3.bullet.CollisionSpace;
import com.jme3.math.Quaternion;
import java.nio.ByteBuffer;
import jme3utilities.Validate;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;

/**
 * A 3-D object to be rendered by V-Sport, including a mesh, a texture, a shader
 * program, a coordinate transform, and 2 colors.
 */
public class Geometry {
    // *************************************************************************
    // fields

    /**
     * true to enable depth test, false to disable it
     */
    private boolean depthTest = true;
    /**
     * true to enable back-face culling, false to disable it
     */
    private boolean isBackCulling = true;
    /**
     * true to enable front-face culling, false to disable it
     */
    private boolean isFrontCulling;
    /**
     * true to enable wireframe rendering, false to disable it
     */
    private boolean wireframe;
    /**
     * draw mode and vertex data for visualization
     */
    private Mesh mesh;
    /**
     * values to be written to the non-global UBO
     */
    final private NonGlobalUniformValues uniformValues
            = new NonGlobalUniformValues();
    /**
     * rendering program
     */
    private ShaderProgram program;
    /**
     * primary texture (typically diffuse color) or null if none
     */
    private Texture texture;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a geometry with the specified mesh and the default
     * ShaderProgram and make it visible.
     *
     * @param mesh the desired Mesh (not null, alias created)
     */
    public Geometry(Mesh mesh) {
        this();
        Validate.nonNull(mesh, "mesh");

        this.mesh = mesh;
        BaseApplication.makeVisible(this);
    }

    /**
     * Instantiate a geometry with no mesh and the default ShaderProgram. Don't
     * make it visible.
     */
    protected Geometry() {
        this.program = getDefaultProgram();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the alpha discard threshold.
     *
     * @return the threshold value
     */
    public float alphaDiscardThreshold() {
        return uniformValues.alphaDiscardThreshold();
    }

    /**
     * Return a copy of the mesh-to-world scale factors.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a vector of scale factors (either {@code storeResult} or a new
     * instance, not null)
     */
    public Vector3f copyScale(Vector3f storeResult) {
        Vector3f result = uniformValues.copyScale(storeResult);
        return result;
    }

    /**
     * Return a copy of the mesh-to-world scale factors.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a vector of scale factors (either {@code storeResult} or a new
     * instance, not null)
     */
    public com.jme3.math.Vector3f copyScaleJme(
            com.jme3.math.Vector3f storeResult) {
        com.jme3.math.Vector3f result = uniformValues.copyScaleJme(storeResult);
        return result;
    }

    /**
     * Return a copy of the mesh-to-world coordinate transform.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return the transform (either {@code storeResult} or a new matrix, not
     * null)
     */
    public Matrix4f copyTransform(Matrix4f storeResult) {
        Matrix4f result = uniformValues.copyTransform(storeResult);
        return result;
    }

    /**
     * Access the primary texture, if any.
     *
     * @return the pre-existing instance (may be null)
     */
    Texture findTexture() {
        return texture;
    }

    /**
     * Generate a rasterization-state create buffer.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer
     */
    VkPipelineRasterizationStateCreateInfo
            generateRasterizationState(MemoryStack stack) {
        VkPipelineRasterizationStateCreateInfo result
                = VkPipelineRasterizationStateCreateInfo.calloc(stack);
        result.sType(
                VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
        );

        result.depthBiasEnable(false);
        result.depthClampEnable(false);
        result.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE); // TODO use this
        result.lineWidth(1f); // in fragments
        result.rasterizerDiscardEnable(false);

        int cullMode = VK10.VK_CULL_MODE_NONE;
        if (isBackCulling) {
            cullMode |= VK10.VK_CULL_MODE_BACK_BIT;
        }
        if (isFrontCulling) {
            cullMode |= VK10.VK_CULL_MODE_FRONT_BIT;
        }
        result.cullMode(cullMode);

        int polygonMode;
        if (wireframe) {
            polygonMode = VK10.VK_POLYGON_MODE_LINE;
        } else {
            polygonMode = VK10.VK_POLYGON_MODE_FILL;
        }
        result.polygonMode(polygonMode);

        return result;
    }

    /**
     * Access the Mesh.
     *
     * @return the pre-existing object (not null)
     */
    public Mesh getMesh() {
        assert mesh != null;
        return mesh;
    }

    /**
     * Access the shader program.
     *
     * @return the pre-existing instance (not null)
     */
    ShaderProgram getProgram() {
        assert program != null;
        return program;
    }

    /**
     * Test whether back-face culling is enabled.
     *
     * @return true if enabled, otherwise false
     */
    public boolean isBackCulling() {
        return isBackCulling;
    }

    /**
     * Test whether depth test is enabled.
     *
     * @return true if enabled, otherwise false
     */
    public boolean isDepthTest() {
        return depthTest;
    }

    /**
     * Test whether front-face culling is enabled.
     *
     * @return true if enabled, otherwise false
     */
    public boolean isFrontCulling() {
        return isFrontCulling;
    }

    /**
     * Test whether wireframe mode is enabled.
     *
     * @return true if enabled, otherwise false
     */
    public boolean isWireframe() {
        return wireframe;
    }

    /**
     * Return a copy of the location of the mesh origin.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in worldspace (either {@code storeResult} or a
     * new vector, not null)
     */
    public Vector3f location(Vector3f storeResult) {
        Vector3f result = uniformValues.location(storeResult);
        return result;
    }

    /**
     * Return a copy of the location of the mesh origin.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a location vector in worldspace (either {@code storeResult} or a
     * new vector, not null)
     */
    public com.jme3.math.Vector3f locationJme(
            com.jme3.math.Vector3f storeResult) {
        com.jme3.math.Vector3f result = uniformValues.locationJme(storeResult);
        return result;
    }

    /**
     * Translate by the specified offset without changing the orientation.
     *
     * @param offset the offset (in worldspace, not null, finite, unaffected)
     */
    public void move(com.jme3.math.Vector3f offset) {
        Validate.finite(offset, "offset");
        uniformValues.move(offset.x, offset.y, offset.z);
    }

    /**
     * Translate by the specified offset without changing the orientation.
     *
     * @param offset the offset (in worldspace, not null, finite, unaffected)
     */
    public void move(Vector3fc offset) {
        Validate.require(offset.isFinite(), "a finite offset");
        uniformValues.move(offset.x(), offset.y(), offset.z());
    }

    /**
     * Return a copy of the mesh-to-world coordinate rotation.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a unit quaternion (either {@code storeResult} or a new
     * quaternion)
     */
    public Quaternionf orientation(Quaternionf storeResult) {
        Quaternionf result = uniformValues.orientation(storeResult);
        return result;
    }

    /**
     * Return a copy of the mesh-to-world coordinate rotation.
     *
     * @param storeResult storage for the result (modified if not null)
     * @return a unit quaternion (either {@code storeResult} or a new
     * quaternion)
     */
    public Quaternion orientationJme(Quaternion storeResult) {
        Quaternion result = uniformValues.orientationJme(storeResult);
        return result;
    }

    /**
     * Return the point size for sprites.
     *
     * @return the size (in pixels)
     */
    public float pointSize() {
        float result = uniformValues.pointSize();
        return result;
    }

    /**
     * Reset the model transform so that meshspace and worldspace are the same.
     *
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry resetModelTransform() {
        uniformValues.resetModelTransform();
        return this;
    }

    /**
     * Rotate the model by the specified angle around the specified axis,
     * without translating the mesh origin.
     * <p>
     * The rotation axis is assumed to be a unit vector.
     *
     * @param angle the rotation angle (in radians, 0&rarr;no effect)
     * @param x the X component of the rotation axis
     * @param y the Y component of the rotation axis
     * @param z the Z component of the rotation axis
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry rotate(float angle, float x, float y, float z) {
        uniformValues.rotateAngleAxis(angle, x, y, z);
        return this;
    }

    /**
     * Apply the specified rotation, without translating the mesh origin.
     *
     * @param rotation the rotation to apply (not null, each row is a unit
     * vector, unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry rotate(Matrix3fc rotation) {
        Validate.nonNull(rotation, "rotation");
        uniformValues.rotate(rotation);
        return this;
    }

    /**
     * Uniformly scale the model by the specified factor.
     *
     * @param factor the scaling factor (1&rarr;no effect)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry scale(float factor) {
        uniformValues.scale(factor, factor, factor);
        return this;
    }

    /**
     * Alter the alpha discard threshold.
     *
     * @param threshold the desired threshold (default=0.5)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setAlphaDiscardThreshold(float threshold) {
        uniformValues.setAlphaDiscardThreshold(threshold);
        return this;
    }

    /**
     * Enable or disable back-face culling.
     *
     * @param newSetting true to enable, false to disable (default=true)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setBackCulling(boolean newSetting) {
        this.isBackCulling = newSetting;
        return this;
    }

    /**
     * Alter the base color.
     *
     * @param color the desired color (in the Linear colorspace, not null,
     * unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setColor(Vector4fc color) {
        Validate.nonNull(color, "color");
        uniformValues.setColor(color.x(), color.y(), color.z(), color.w());
        return this;
    }

    /**
     * Enable or disable depth testing.
     *
     * @param newSetting true to enable, false to disable (default=true)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setDepthTest(boolean newSetting) {
        if (newSetting != depthTest) {
            this.depthTest = newSetting;
            BaseApplication.updateDeferredQueue(this);
        }

        return this;
    }

    /**
     * Enable or disable front-face culling.
     *
     * @param newSetting true to enable, false to disable (default=false)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setFrontCulling(boolean newSetting) {
        this.isFrontCulling = newSetting;
        return this;
    }

    /**
     * Alter the location of the mesh origin.
     *
     * @param x the desired X coordinate (in worldspace, finite, default=0)
     * @param y the desired Y coordinate (in worldspace, finite, default=0)
     * @param z the desired Z coordinate (in worldspace, finite, default=0)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setLocation(float x, float y, float z) {
        Validate.finite(x, "x");
        Validate.finite(y, "y");
        Validate.finite(z, "z");

        uniformValues.setLocation(x, y, z);
        return this;
    }

    /**
     * Translate the mesh origin to the specified location.
     *
     * @param location the desired location (in worldspace, not null, finite,
     * unaffected, default=(0,0,0))
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setLocation(com.jme3.math.Vector3f location) {
        Validate.finite(location, "location");
        uniformValues.setLocation(location.x, location.y, location.z);
        return this;
    }

    /**
     * Translate the mesh origin to the specified location.
     *
     * @param location the desired location (in worldspace, not null, finite,
     * unaffected, default=(0,0,0))
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setLocation(Vector3fc location) {
        Validate.nonNull(location, "location");
        Validate.require(location.isFinite(), "finite location");

        uniformValues.setLocation(location.x(), location.y(), location.z());
        return this;
    }

    /**
     * Replace the geometry's current mesh with the specified one.
     *
     * @param mesh the desired mesh (not null, alias created)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setMesh(Mesh mesh) {
        Validate.nonNull(mesh, "mesh");
        this.mesh = mesh;
        return this;
    }

    /**
     * Alter the orientation using Tait-Bryan angles, applying the rotations in
     * x-z-y extrinsic order or y-z'-x" intrinsic order.
     *
     * @param xAngle the desired X angle (in radians, finite)
     * @param yAngle the desired Y angle (in radians, finite)
     * @param zAngle the desired Z angle (in radians, finite)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(float xAngle, float yAngle, float zAngle) {
        Validate.finite(xAngle, "x angle");
        Validate.finite(xAngle, "y angle");
        Validate.finite(xAngle, "z angle");

        uniformValues.setOrientation(xAngle, yAngle, zAngle);
        return this;
    }

    /**
     * Alter the orientation without translating the mesh origin.
     * <p>
     * The rotation axis is assumed to be a unit vector.
     *
     * @param angle the desired rotation angle (in radians, finite, default=0)
     * @param x the X component of the rotation axis (&ge;-1, &le;1)
     * @param y the Y component of the rotation axis (&ge;-1, &le;1)
     * @param z the Z component of the rotation axis (&ge;-1, &le;1)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(float angle, float x, float y, float z) {
        Validate.finite(angle, "angle");
        Validate.inRange(x, "x", -1f, 1f);
        Validate.inRange(y, "y", -1f, 1f);
        Validate.inRange(z, "z", -1f, 1f);

        uniformValues.setOrientationAngleAxis(angle, x, y, z);
        return this;
    }

    /**
     * Alter the orientation without translating the mesh origin.
     *
     * @param orientation the desired orientation (not null, each row is a unit
     * vector, unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(Matrix3fc orientation) {
        Validate.nonNull(orientation, "orientation");
        uniformValues.setOrientation(orientation);
        return this;
    }

    /**
     * Alter the orientation without translating the mesh origin.
     *
     * @param orientation the desired orientation (not null, not zero,
     * unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(Quaternion orientation) {
        Validate.nonZero(orientation, "orientation");
        uniformValues.setOrientationQuaternion(orientation.getX(),
                orientation.getY(), orientation.getZ(), orientation.getW());
        return this;
    }

    /**
     * Alter the orientation without translating the mesh origin.
     *
     * @param orientation the desired orientation (not null, not zero,
     * unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(Quaternionfc orientation) {
        Validate.nonNull(orientation, "orientation");
        uniformValues.setOrientationQuaternion(orientation.x(), orientation.y(),
                orientation.z(), orientation.w());
        return this;
    }

    /**
     * Alter the orientation without translating the mesh origin.
     * <p>
     * The arguments are assumed to form an orthonormal basis.
     *
     * @param xDir the desired direction of the mesh X axis (unit vector in
     * world coordinates, not null, unaffected)
     * @param yDir the desired direction of the mesh Y axis (unit vector in
     * world coordinates, not null, unaffected)
     * @param zDir the desired direction of the mesh Z axis (unit vector in
     * world coordinates, not null, unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setOrientation(com.jme3.math.Vector3f xDir,
            com.jme3.math.Vector3f yDir, com.jme3.math.Vector3f zDir) {
        Validate.nonZero(xDir, "x direction");
        Validate.nonZero(yDir, "y direction");
        Validate.nonZero(zDir, "z direction");

        com.jme3.math.Quaternion q = new Quaternion();
        q.fromAxes(xDir, yDir, zDir);
        uniformValues.setOrientationQuaternion(
                q.getX(), q.getY(), q.getZ(), q.getW());

        return this;
    }

    /**
     * Alter the point size for sprites.
     *
     * @param size the desired size (in pixels, default=32)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setPointSize(float size) {
        uniformValues.setPointSize(size);
        return this;
    }

    /**
     * Replace the geometry's current shader program with the named program, or
     * if the name is null, replace it with the default program.
     *
     * @param name the name of the desired program (may be null)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setProgram(String name) {
        if (name == null) {
            this.program = getDefaultProgram();
        } else {
            this.program = BaseApplication.getProgram(name);
        }

        return this;
    }

    /**
     * Alter the mesh-to-world scale factors.
     *
     * @param scaleFactor the desired mesh-to-world scale factor for all axes
     * (finite, default=1)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setScale(float scaleFactor) {
        uniformValues.setScale(scaleFactor, scaleFactor, scaleFactor);
        return this;
    }

    /**
     * Alter the mesh-to-world scale factors.
     *
     * @param scaleFactors the desired scale factor for each mesh axis (not
     * null, finite, unaffected, default=(1,1,1))
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setScale(com.jme3.math.Vector3f scaleFactors) {
        Validate.finite(scaleFactors, "scale factors");
        uniformValues.setScale(
                scaleFactors.x, scaleFactors.y, scaleFactors.z);
        return this;
    }

    /**
     * Alter the mesh-to-world scale factors.
     *
     * @param scaleFactors the desired scale factor for each mesh axis (not
     * null, finite, unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setScale(Vector3fc scaleFactors) {
        Validate.nonNull(scaleFactors, "scale factors");
        Validate.require(scaleFactors.isFinite(), "finite scale factors");

        uniformValues.setScale(
                scaleFactors.x(), scaleFactors.y(), scaleFactors.z());
        return this;
    }

    /**
     * Alter the specular color.
     *
     * @param color the desired color (in the Linear colorspace, not null,
     * unaffected)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setSpecularColor(Vector4fc color) {
        Validate.nonNull(color, "color");
        uniformValues.setSpecularColor(
                color.x(), color.y(), color.z(), color.w());
        return this;
    }

    /**
     * Replace the primary texture with one obtained using the specified key.
     *
     * @param textureKey a key to obtain the desired texture (not null)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setTexture(TextureKey textureKey) {
        Validate.nonNull(textureKey, "texture key");
        this.texture = BaseApplication.getTexture(textureKey);
        return this;
    }

    /**
     * Enable or disable wireframe mode.
     *
     * @param newSetting true to enable, false to disable (default=false)
     * @return the (modified) current geometry (for chaining)
     */
    public Geometry setWireframe(boolean newSetting) {
        this.wireframe = newSetting;
        return this;
    }

    /**
     * Update properties before recording a draw command for the geometry.
     * <p>
     * Meant to be overridden. Can't be used to enable or disable the depth
     * test.
     */
    public void updateAndRender() {
        // do nothing
    }

    /**
     * Test whether the physics object (if any) has been removed from the
     * specified CollisionSpace. Meant to be overridden.
     *
     * @param space the CollisionSpace to test (not null)
     * @return true if removed, otherwise false
     */
    public boolean wasRemovedFrom(CollisionSpace space) {
        return false;
    }

    /**
     * Write the uniform values to the specified UBO.
     *
     * @param ubo the target resource (not null)
     */
    void writeUniformValuesTo(BufferResource ubo) {
        ByteBuffer byteBuffer = ubo.findData();
        uniformValues.writeTo(byteBuffer);
    }
    // *************************************************************************
    // private methods

    /**
     * Return the default ShaderProgram for new geometries.
     *
     * @return a valid program (not null)
     */
    private static ShaderProgram getDefaultProgram() {
        ShaderProgram result
                = BaseApplication.getProgram("Phong/Distant/Monochrome");
        return result;
    }
}
