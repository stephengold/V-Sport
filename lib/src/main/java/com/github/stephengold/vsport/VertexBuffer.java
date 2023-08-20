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

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;
import jme3utilities.Validate;
import jme3utilities.math.MyBuffer;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import jme3utilities.math.MyVector3f;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.vulkan.VK10;

/**
 * A vertex buffer in a V-Sport mesh.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class VertexBuffer {
    // *************************************************************************
    // fields

    /**
     * true if the data store needs updating, otherwise false
     */
    private boolean isModified = true;
    /**
     * true for mutable, or false if immutable
     */
    private boolean isMutable;
    /**
     * buffer resource
     */
    final private BufferResource bufferResource;
    /**
     * buffer data
     */
    final private FloatBuffer dataBuffer;
    /**
     * number of floats per vertex (&ge;1, &le;4)
     */
    final private int fpv;
    /**
     * number of vertices (based on buffer size, unmodified by indexing)
     */
    final private int vertexCount;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a VertexBuffer by wrapping the specified FloatBuffer.
     *
     * @param dataBuffer the FloatBuffer to wrap (not null, alias created,
     * capacity a multiple of {@code fpv})
     * @param fpv number of float values per vertex (&ge;1, &le;4)
     * @param bufferResource (not null, alias created)
     */
    private VertexBuffer(
            FloatBuffer dataBuffer, int fpv, BufferResource bufferResource) {
        assert fpv >= 1 : fpv;
        assert fpv <= 4 : fpv;
        assert bufferResource != null;
        int capacity = dataBuffer.capacity();
        assert (capacity % fpv) == 0 : "capacity=" + capacity + ", fpv=" + fpv;

        this.isMutable = !dataBuffer.isReadOnly();
        this.bufferResource = bufferResource;
        this.dataBuffer = dataBuffer;
        this.fpv = fpv;
        this.vertexCount = capacity / fpv;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the buffer's capacity.
     *
     * @return the count (in floats, &ge;0)
     */
    public int capacity() {
        int result = dataBuffer.capacity();
        return result;
    }

    /**
     * Flip the buffer. The limit is set to the current read/write position, and
     * then the read/write position is zeroed. The data in the buffer are
     * unaffected.
     *
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer flip() {
        dataBuffer.flip();
        return this;
    }

    /**
     * Return the number of floats per vertex.
     *
     * @return the count (&ge;1, &le;4)
     */
    public int fpv() {
        assert fpv >= 1 : fpv;
        assert fpv <= 4 : fpv;
        return fpv;
    }

    /**
     * Read a float from the specified buffer position. Does not alter the
     * buffer's read/write position.
     *
     * @param position the position from which to read (&ge;0, &lt;limit)
     * @return the value that was read
     */
    public float get(int position) {
        float result = dataBuffer.get(position);
        return result;
    }

    /**
     * Read a Vector3f from the specified buffer position. Does not alter the
     * buffer's read/write position.
     *
     * @param position the position from which to begin reading (&ge;0,
     * &lt;limit-2)
     * @param storeResult storage for the result (modified if not null)
     * @return the value that was read (either {@code storeResult} or a new
     * vector)
     */
    public com.jme3.math.Vector3f get(
            int position, com.jme3.math.Vector3f storeResult) {
        com.jme3.math.Vector3f result = (storeResult == null)
                ? new com.jme3.math.Vector3f() : storeResult;
        MyBuffer.get(dataBuffer, position, storeResult);
        return result;
    }

    /**
     * Read a Vector2f from the specified vertex. Does not alter the buffer's
     * read/write position. Requires fpv=2.
     *
     * @param vertexIndex the index of the vertex to read (&ge;0,
     * &lt;vertexCount)
     * @param storeResult storage for the result (modified if not null)
     * @return the value that was read (either {@code storeResult} or a new
     * vector)
     */
    public Vector2f get2f(int vertexIndex, Vector2f storeResult) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);
        if (fpv != 2) {
            throw new IllegalStateException("fpv = " + fpv);
        }
        Vector2f result = (storeResult == null) ? new Vector2f() : storeResult;

        int startPosition = vertexIndex * fpv;
        result.x = dataBuffer.get(startPosition);
        result.y = dataBuffer.get(startPosition + 1);

        return result;
    }

    /**
     * Read a Vector3f from the specified vertex. Does not alter the buffer's
     * read/write position. Requires fpv=3.
     *
     * @param vertexIndex the index of the vertex to read (&ge;0,
     * &lt;vertexCount)
     * @param storeResult storage for the result (modified if not null)
     * @return the value that was read (either {@code storeResult} or a new
     * vector)
     */
    public Vector3f get3f(int vertexIndex, Vector3f storeResult) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);
        if (fpv != Mesh.numAxes) {
            throw new IllegalStateException("fpv = " + fpv);
        }
        Vector3f result = (storeResult == null) ? new Vector3f() : storeResult;

        int startPosition = vertexIndex * fpv;
        result.x = dataBuffer.get(startPosition + MyVector3f.xAxis);
        result.y = dataBuffer.get(startPosition + MyVector3f.yAxis);
        result.z = dataBuffer.get(startPosition + MyVector3f.zAxis);

        return result;
    }

    /**
     * Access the buffer data.
     *
     * @return the pre-existing buffer
     */
    public FloatBuffer getData() {
        verifyMutable();
        assert dataBuffer != null;
        return dataBuffer;
    }

    /**
     * Access the underlying {@code VkBuffer}.
     *
     * @return the handle of the pre-existing instance (not null)
     */
    long handle() {
        long result = bufferResource.handle();
        return result;
    }

    /**
     * Return the buffer's limit.
     *
     * @return the limit position (&ge;0, &le;capacity)
     */
    public int limit() {
        int result = dataBuffer.limit();
        return result;
    }

    /**
     * Make the buffer immutable.
     *
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer makeImmutable() {
        this.isMutable = false;
        return this;
    }

    /**
     * Create a mutable color buffer from a list of vertices.
     *
     * @param vertices the vertices to use (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newColor(List<Vertex> vertices) {
        int fpv = 3;
        int numVertices = vertices.size();
        VertexBuffer result = newInstance(fpv, numVertices);

        FloatBuffer data = result.getData();
        for (Vertex vertex : vertices) {
            vertex.writeColorTo(data);
        }

        return result;
    }

    /**
     * Create a mutable vertex buffer initialized from an array of floats.
     *
     * @param fpv the number of floats per vertex (&ge;1, &le;4)
     * @param floatArray the initial data (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newInstance(int fpv, float... floatArray) {
        int numVertices = floatArray.length / fpv;
        VertexBuffer result
                = VertexBuffer.newInstance(Mesh.numAxes, numVertices);

        FloatBuffer data = result.getData();
        for (float fValue : floatArray) {
            data.put(fValue);
        }

        return result;
    }

    /**
     * Create a mutable, uninitialized vertex buffer.
     *
     * @param fpv the number of floats per vertex (&ge;1, &le;4)
     * @param numVertices the desired capacity (in vertices)
     * @return a new instance (not null)
     */
    static VertexBuffer newInstance(int fpv, int numVertices) {
        int numFloats = numVertices * fpv;
        int numBytes = numFloats * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        BufferResource bufferResource = new BufferResource(
                numBytes, usage, staging) {
            @Override
            protected void fill(ByteBuffer destinationBuffer) {
                // do nothing
            }
        };
        ByteBuffer byteBuffer = bufferResource.findData();
        byteBuffer.rewind();
        byteBuffer.limit(numBytes);
        FloatBuffer dataBuffer = byteBuffer.asFloatBuffer();
        VertexBuffer result = new VertexBuffer(dataBuffer, fpv, bufferResource);

        return result;
    }

    /**
     * Create a mutable vertex buffer initialized from an array of vectors.
     *
     * @param vectors the initial data (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newInstance(com.jme3.math.Vector3f... vectors) {
        int numVertices = vectors.length;
        VertexBuffer result
                = VertexBuffer.newInstance(Mesh.numAxes, numVertices);
        for (com.jme3.math.Vector3f vector : vectors) {
            result.put(vector);
        }

        return result;
    }

    /**
     * Create a mutable normal buffer from a list of vertices.
     *
     * @param vertices the vertices to use (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newNormal(List<Vertex> vertices) {
        int numVertices = vertices.size();
        int fpv = Mesh.numAxes;
        VertexBuffer result = newInstance(fpv, numVertices);

        FloatBuffer data = result.getData();
        for (Vertex vertex : vertices) {
            vertex.writeNormalTo(data);
        }

        return result;
    }

    /**
     * Create a mutable position buffer from a list of vertices.
     *
     * @param vertices the vertices to use (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newPosition(List<Vertex> vertices) {
        int fpv = Mesh.numAxes;
        int numVertices = vertices.size();
        VertexBuffer result = newInstance(fpv, numVertices);

        FloatBuffer data = result.getData();
        for (Vertex vertex : vertices) {
            vertex.writePositionTo(data);
        }

        return result;
    }

    /**
     * Create a texture-coordinates buffer from a list of vertices.
     *
     * @param vertices the vertices to use (not null, unaffected)
     * @return a new instance (not null)
     */
    static VertexBuffer newTexCoords(List<Vertex> vertices) {
        int fpv = 2;
        int numVertices = vertices.size();
        VertexBuffer result = newInstance(fpv, numVertices);

        FloatBuffer data = result.getData();
        for (Vertex vertex : vertices) {
            vertex.writeTexCoordsTo(data);
        }

        return result;
    }

    /**
     * Return the buffer's read/write position.
     *
     * @return the position (&ge;0, &le;limit)
     */
    public int position() {
        int result = dataBuffer.position();
        return result;
    }

    /**
     * Write the specified value at the current read/write position, then
     * increment the position.
     *
     * @param fValue the value to be written
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put(float fValue) {
        verifyMutable();

        dataBuffer.put(fValue);
        setModified();

        return this;
    }

    /**
     * Write the specified value at the specified buffer position. Does not
     * alter the buffer's read/write position.
     *
     * @param position the position to write to (&ge;0, &lt;limit)
     * @param fValue the value to write
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put(int position, float fValue) {
        verifyMutable();

        dataBuffer.put(position, fValue);
        setModified();

        return this;
    }

    /**
     * Write the specified vector at the current read/write position, then
     * increment the position by 3.
     *
     * @param vector the value to be written (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put(Vector3fc vector) {
        verifyMutable();

        dataBuffer.put(vector.x());
        dataBuffer.put(vector.y());
        dataBuffer.put(vector.z());
        setModified();

        return this;
    }

    /**
     * Write the specified vector at the current read/write position, then
     * increment the position by 3.
     *
     * @param vector the value to be written (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put(com.jme3.math.Vector3f vector) {
        verifyMutable();

        dataBuffer.put(vector.x);
        dataBuffer.put(vector.y);
        dataBuffer.put(vector.z);
        setModified();

        return this;
    }

    /**
     * Write the specified vector at the current read/write position, then
     * increment the position by 3.
     *
     * @param vector the value to be written (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put(Vector3fc vector) {
        verifyMutable();

        dataBuffer.put(vector.x());
        dataBuffer.put(vector.y());
        dataBuffer.put(vector.z());
        setModified();

        return this;
    }

    /**
     * Write the specified vector to the specified vertex. Does not alter the
     * buffer's read/write position. Requires fpv=2.
     *
     * @param vertexIndex the index of the vertex to write (&ge;0,
     * &lt;vertexCount)
     * @param vector the vector to write (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put2f(int vertexIndex, Vector2fc vector) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);
        Validate.nonNull(vector, "vector");
        verifyMutable();
        if (fpv != 2) {
            throw new IllegalStateException("fpv = " + fpv);
        }

        int startPosition = vertexIndex * fpv;
        dataBuffer.put(startPosition + MyVector3f.xAxis, vector.x());
        dataBuffer.put(startPosition + MyVector3f.yAxis, vector.y());
        setModified();

        return this;
    }

    /**
     * Write the specified vector to the specified vertex. Does not alter the
     * buffer's read/write position. Requires fpv=3.
     *
     * @param vertexIndex the index of the vertex to write (&ge;0,
     * &lt;vertexCount)
     * @param vector the vector to write (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer put3f(int vertexIndex, Vector3fc vector) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);
        Validate.nonNull(vector, "vector");
        verifyMutable();
        if (fpv != Mesh.numAxes) {
            throw new IllegalStateException("fpv = " + fpv);
        }

        int startPosition = vertexIndex * fpv;
        dataBuffer.put(startPosition + MyVector3f.xAxis, vector.x());
        dataBuffer.put(startPosition + MyVector3f.yAxis, vector.y());
        dataBuffer.put(startPosition + MyVector3f.zAxis, vector.z());
        setModified();

        return this;
    }

    /**
     * Write the specified floats to the specified vertex. Does not alter the
     * buffer's read/write position.
     *
     * @param vertexIndex the index of the vertex to write (&ge;0, &lt;limit)
     * @param floatArray the floats to write (not null, length=fpv, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer putArray(int vertexIndex, float... floatArray) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);
        Validate.nonNull(floatArray, "float array");
        verifyMutable();
        if (fpv != floatArray.length) {
            throw new IllegalStateException("fpv = " + fpv);
        }

        int bufferPosition = vertexIndex * fpv;
        for (float fValue : floatArray) {
            dataBuffer.put(bufferPosition, fValue);
            ++bufferPosition;
        }
        setModified();

        return this;
    }

    /**
     * Apply the specified 3-D rotation to all vertices. Requires fpv=3.
     *
     * @param quaternion the rotation to apply (not null, not zero, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer rotate(Quaternion quaternion) {
        Validate.nonZero(quaternion, "quaternion");
        if (MyQuaternion.isRotationIdentity(quaternion)) {
            return this;
        }
        verifyMutable();
        if (fpv != Mesh.numAxes) {
            throw new IllegalStateException("fpv = " + fpv);
        }

        int numFloats = capacity();
        MyBuffer.rotate(dataBuffer, 0, numFloats, quaternion);
        setModified();

        return this;
    }

    /**
     * Apply the specified scaling to all vertices.
     *
     * @param scaleFactor the scale factor to apply (&ge;0, finite)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer scale(float scaleFactor) {
        Validate.nonNegative(scaleFactor, "scale factor");
        Validate.finite(scaleFactor, "scale factor");
        if (scaleFactor == 1f) {
            return this;
        }
        verifyMutable();

        int numFloats = capacity();
        for (int floatIndex = 0; floatIndex < numFloats; ++floatIndex) {
            float floatValue = get(floatIndex);
            floatValue *= scaleFactor;
            put(floatIndex, floatValue);
        }
        setModified();

        return this;
    }

    /**
     * Indicate that the buffer data have changed.
     *
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer setModified() {
        verifyMutable();
        this.isModified = true;
        return this;
    }

    /**
     * Apply the specified 3-D transform to all vertices. Requires fpv=3.
     *
     * @param transform the transform to apply (not null, unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public VertexBuffer transform(Transform transform) {
        Validate.nonNull(transform, "transform");
        if (MyMath.isIdentity(transform)) {
            return this;
        }
        verifyMutable();
        if (fpv != Mesh.numAxes) {
            throw new IllegalStateException("fpv = " + fpv);
        }

        int numFloats = capacity();
        MyBuffer.transform(dataBuffer, 0, numFloats, transform);
        setModified();

        return this;
    }

    /**
     * Verify that the buffer is still mutable.
     */
    public void verifyMutable() {
        if (!isMutable) {
            throw new IllegalStateException(
                    "The vertex buffer is no longer mutable.");
        }
    }
}
