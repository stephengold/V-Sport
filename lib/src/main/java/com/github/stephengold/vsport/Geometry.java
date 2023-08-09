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
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

/**
 * A 3-D object to be rendered by V-Sport, including a Mesh, a Texture, and a
 * ShaderProgram.
 */
public class Geometry {
    // *************************************************************************
    // fields

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
     * color material texture
     */
    private Texture texture;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a Geometry with the specified Mesh and default ShaderProgram
     * and make it visible.
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
     * Instantiate a Geometry with no Mesh and default ShaderProgram. Don't make
     * it visible.
     */
    protected Geometry() {
        this.program = getDefaultProgram();
    }
    // *************************************************************************
    // new methods exposed

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
     * Access the Mesh.
     *
     * @return the pre-existing object (not null)
     */
    public Mesh getMesh() {
        assert mesh != null;
        return mesh;
    }

    /**
     * Access the ShaderProgram.
     *
     * @return the pre-existing instance (not null)
     */
    ShaderProgram getProgram() {
        assert program != null;
        return program;
    }

    /**
     * Access the Texture.
     *
     * @return the pre-existing instance (not null)
     */
    Texture getTexture() {
        assert texture != null;
        return texture;
    }

    /**
     * Reset the model transform so that mesh coordinates and world coordinates
     * are the same.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Geometry resetModelTransform() {
        uniformValues.resetModelTransform();
        return this;
    }

    /**
     * Rotate the model by the specified angle around the specified axis.
     * <p>
     * The rotation axis is assumed to be a unit vector.
     *
     * @param angle the rotation angle (in radians, 0 = no effect)
     * @param x the X component of the rotation axis
     * @param y the Y component of the rotation axis
     * @param z the Z component of the rotation axis
     * @return the (modified) current instance (for chaining)
     */
    public Geometry rotate(float angle, float x, float y, float z) {
        uniformValues.rotate(angle, x, y, z);
        return this;
    }

    /**
     * Apply the specified rotation, without shifting the local origin.
     *
     * @param rotation the rotation to apply (not null, each row is a unit
     * vector, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry rotate(Matrix3fc rotation) {
        uniformValues.rotate(rotation);
        return this;
    }

    /**
     * Replace the geometry's Mesh with the specified Mesh.
     *
     * @param newMesh the desired Mesh (not null, alias created)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setMesh(Mesh newMesh) {
        Validate.nonNull(newMesh, "new mesh");
        this.mesh = newMesh;
        return this;
    }

    /**
     * Replace the geometry's shader program with the named ShaderProgram, or if
     * the name is null, replace it with the default program.
     *
     * @param name (may be null)
     * @return the (modified) current instance (for chaining)
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
     * Scale the model by the specified factor.
     *
     * @param factor the scaling factor (1 = no effect)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry scale(float factor) {
        uniformValues.scale(factor);
        return this;
    }

    /**
     * Alter the mesh-to-world coordinate rotation.
     * <p>
     * The axis is assumed to be a unit vector.
     *
     * @param angle the desired rotation angle (in radians)
     * @param x the X component of the rotation axis
     * @param y the Y component of the rotation axis
     * @param z the Z component of the rotation axis
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setOrientation(float angle, float x, float y, float z) {
        uniformValues.setOrientation(angle, x, y, z);
        return this;
    }

    /**
     * Alter the mesh-to-world coordinate rotation, without shifting the local
     * origin.
     *
     * @param desiredOrientation the desired orientation (not null, each row is
     * a unit vector, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setOrientation(Matrix3fc desiredOrientation) {
        uniformValues.setOrientation(desiredOrientation);
        return this;
    }

    /**
     * Replace the geometry's primary Texture with one obtained using the
     * specified key.
     *
     * @param textureKey a key to obtain the desired texture (not null)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setTexture(TextureKey textureKey) {
        Validate.nonNull(textureKey, "texture key");
        this.texture = BaseApplication.getTexture(textureKey);
        return this;
    }

    /**
     * Translate the mesh origin to the specified location.
     *
     * @param x the desired X coordinate (in world coordinates)
     * @param y the desired Y coordinate (in world coordinates)
     * @param z the desired Z coordinate (in world coordinates)
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setLocation(float x, float y, float z) {
        uniformValues.setLocation(x, y, z);
        return this;
    }

    /**
     * Translate the mesh origin to the specified location.
     *
     * @param desiredLocation the desired location (in world coordinates, not
     * @return the (modified) current instance (for chaining)
     */
    public Geometry setLocation(Vector3fc desiredLocation) {
        Validate.nonNull(desiredLocation, "desired location");
        uniformValues.setLocation(desiredLocation);
        return this;
    }

    /**
     * Write the uniform values to the specified UBO.
     *
     * @param ubo the target resource (not null)
     */
    void writeUniformValuesTo(BufferResource ubo) {
        ByteBuffer byteBuffer = ubo.getData();
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
