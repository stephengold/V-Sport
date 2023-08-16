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

import com.jme3.math.Quaternion;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jme3utilities.Validate;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;

/**
 * A mesh composed of triangles, with optional indices, vertex colors, normals,
 * and texture coordinates.
 */
public class Mesh implements jme3utilities.lbj.Mesh {
    // *************************************************************************
    // constants

    /**
     * number of axes in a 3-D vector
     */
    final protected static int numAxes = 3;
    /**
     * number of vertices per edge (line)
     */
    final public static int vpe = 2;
    /**
     * number of vertices per triangle
     */
    final public static int vpt = 3;
    // *************************************************************************
    // fields

    /**
     * true for a mutable mesh, or false if immutable
     */
    private boolean mutable = true;
    /**
     * vertex indices, or null if none
     */
    private IndexBuffer indexBuffer;
    /**
     * number of vertices (based on buffer sizes, unmodified by indexing)
     */
    final private int vertexCount;
    /**
     * how vertices are organized into primitives (not null)
     */
    private Topology topology;
    /**
     * vertex colors (3 floats per vertex) or null if not present
     */
    private VertexBuffer colorBuffer;
    /**
     * vertex normals (3 floats per vertex) or null if not present
     */
    private VertexBuffer normalBuffer;
    /**
     * vertex positions (3 floats per vertex)
     */
    private VertexBuffer positionBuffer;
    /**
     * vertex texture coordinates (2 floats per vertex) or null if not present
     */
    private VertexBuffer texCoordsBuffer;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a mutable mesh from vertices and optional indices.
     *
     * @param topology the desired primitive topology (not null)
     * @param indices the vertex indices to use (unaffected) or null if none
     * @param vertices the vertex data to use (not null, unaffected)
     */
    public Mesh(
            Topology topology, List<Integer> indices, List<Vertex> vertices) {
        Validate.nonNull(topology, "topology");

        this.topology = topology;
        this.vertexCount = vertices.size();
        if (indices == null) {
            this.indexBuffer = null;
        } else {
            this.indexBuffer = IndexBuffer.newInstance(indices);
        }

        this.positionBuffer = VertexBuffer.newPosition(vertices);
        Vertex representativeVertex = vertices.get(0);

        // color buffer:
        boolean hasColor = representativeVertex.hasColor();
        if (hasColor) {
            this.colorBuffer = VertexBuffer.newColor(vertices);
        } else {
            this.colorBuffer = null;
        }

        // normal buffer:
        boolean hasNormal = representativeVertex.hasNormal();
        if (hasNormal) {
            this.normalBuffer = VertexBuffer.newNormal(vertices);
        } else {
            this.normalBuffer = null;
        }

        // texture-coordinates buffer:
        boolean hasTexCoords = representativeVertex.hasTexCoords();
        if (hasTexCoords) {
            this.texCoordsBuffer = VertexBuffer.newTexCoords(vertices);
        } else {
            this.texCoordsBuffer = null;
        }

        assert mutable;
    }

    /**
     * Instantiate an incomplete mutable mesh with the specified topology and
     * number of vertices, but no indices, positions, colors, normals, or
     * texture coordinates.
     *
     * @param topology the desired primitive topology (not null)
     * @param vertexCount number of vertices (&ge;0)
     */
    protected Mesh(Topology topology, int vertexCount) {
        Validate.nonNull(topology, "topology");
        Validate.nonNegative(vertexCount, "vertex count");

        this.topology = topology;
        this.vertexCount = vertexCount;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Copy a single vertex from the mesh.
     *
     * @param vertexIndex the vertex index (&ge;0, &lt;vertexCount)
     * @return a new vertex
     */
    Vertex copyVertex(int vertexIndex) {
        Validate.inRange(vertexIndex, "vertex index", 0, vertexCount - 1);

        Vector3fc position = positionBuffer.get3f(vertexIndex, null);

        Vector3fc color = null;
        if (colorBuffer != null) {
            color = colorBuffer.get3f(vertexIndex, null);
        }

        Vector3fc normal = null;
        if (normalBuffer != null) {
            normal = normalBuffer.get3f(vertexIndex, null);
        }

        Vector2fc texCoords = null;
        if (texCoordsBuffer != null) {
            texCoords = texCoordsBuffer.get2f(vertexIndex, null);
        }

        Vertex result = new Vertex(position, color, normal, texCoords);
        return result;
    }

    /**
     * Count how many attributes the mesh contains.
     *
     * @return the count (&gt;0)
     */
    int countAttributes() {
        int result = 1; // for the position buffer
        if (normalBuffer != null) {
            ++result;
        }
        if (texCoordsBuffer != null) {
            ++result;
        }
        if (colorBuffer != null) {
            ++result;
        }

        return result;
    }

    /**
     * Count how many vertices the mesh renders, taking indexing into account,
     * but not the topology.
     *
     * @return the count (&ge;0)
     */
    int countIndexedVertices() {
        int result = (indexBuffer == null) ? vertexCount : indexBuffer.size();
        return result;
    }

    /**
     * Count how many line primitives the mesh contains.
     *
     * @return the count (&ge;0)
     */
    public int countLines() {
        int result;
        int vpp = topology.vpp();
        if (vpp == 2) {
            int numIndices = countIndexedVertices();
            int numShared = topology.numShared();
            result = (numIndices - numShared) / (vpp - numShared);
        } else {
            result = 0;
        }

        return result;
    }

    /**
     * Count how many triangle primitives the mesh contains.
     *
     * @return the count (&ge;0)
     */
    public int countTriangles() {
        int result;
        int vpp = topology.vpp();
        if (vpp == 3) {
            int numIndices = countIndexedVertices();
            int numShared = topology.numShared();
            result = (numIndices - numShared) / (vpp - numShared);
        } else {
            result = 0;
        }

        return result;
    }

    /**
     * Count how many vertices the mesh contains, based on buffer capacities,
     * unmodified by primitive topology and indexing.
     *
     * @return the count (&ge;0)
     */
    public int countVertices() {
        return vertexCount;
    }

    /**
     * Remove the normals, if any.
     */
    public void dropNormals() {
        verifyMutable();
        this.normalBuffer = null;
    }

    /**
     * Remove the texture coordinates, if any.
     */
    public void dropTexCoords() {
        verifyMutable();
        this.texCoordsBuffer = null;
    }

    /**
     * Generate buffer handles for the BindVertexBuffers command.
     *
     * @param program the shader program to be run (not null, unaffected)
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer
     */
    LongBuffer generateBufferHandles(ShaderProgram program, MemoryStack stack) {
        int numAttributes = program.countAttributes();
        LongBuffer result = stack.mallocLong(numAttributes);

        int slotIndex = 0;
        result.put(slotIndex, positionBuffer.handle());
        if (program.requiresColor()) {
            if (colorBuffer == null) {
                throw new IllegalStateException(
                        "Geometry cannot be rendered because the " + program
                        + " program requires vertex colors.");
            }
            ++slotIndex;
            result.put(slotIndex, colorBuffer.handle());
        }
        if (program.requiresNormal()) {
            if (normalBuffer == null) {
                throw new IllegalStateException(
                        "Geometry cannot be rendered because the " + program
                        + " program requires vertex normals.");
            }
            ++slotIndex;
            result.put(slotIndex, normalBuffer.handle());
        }
        if (program.requiresTexCoords()) {
            if (texCoordsBuffer == null) {
                throw new IllegalStateException(
                        "Geometry cannot be rendered because the " + program
                        + " program requires texture coordinates.");
            }
            ++slotIndex;
            result.put(slotIndex, texCoordsBuffer.handle());
        }

        return result;
    }

    /**
     * Generate create info for the input-assembly state of a graphics pipeline.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new, temporary struct (not null)
     */
    VkPipelineInputAssemblyStateCreateInfo
            generateIasCreateInfo(MemoryStack stack) {
        VkPipelineInputAssemblyStateCreateInfo result
                = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
        result.sType(
                VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
        );

        result.primitiveRestartEnable(false);
        int topologyCode = topology.code();
        result.topology(topologyCode);

        return result;
    }

    /**
     * Generate normals on a vertex-by-vertex basis for an outward-facing
     * sphere. Any pre-existing normals are discarded.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Mesh generateSphereNormals() {
        verifyMutable();
        createNormals();

        Vector3f tmpVector = new Vector3f();
        for (int vertexIndex = 0; vertexIndex < vertexCount; ++vertexIndex) {
            positionBuffer.get3f(vertexIndex, tmpVector);
            tmpVector.normalize();
            normalBuffer.put3f(vertexIndex, tmpVector);
        }

        return this;
    }

    /**
     * Test whether the mesh is indexed.
     *
     * @return true if indexed, otherwise false
     */
    boolean isIndexed() {
        if (indexBuffer == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Make the mesh immutable.
     *
     * @return the (modified) current instance (for chaining)
     */
    public Mesh makeImmutable() {
        this.mutable = false;
        positionBuffer.makeImmutable();
        if (indexBuffer != null) {
            indexBuffer.makeImmutable();
        }
        if (colorBuffer != null) {
            colorBuffer.makeImmutable();
        }
        if (normalBuffer != null) {
            normalBuffer.makeImmutable();
        }
        if (texCoordsBuffer != null) {
            texCoordsBuffer.makeImmutable();
        }

        return this;
    }

    /**
     * Create a mutable mesh by de-duplicating a list of vertices.
     *
     * @param topology the desired primitive topology (not null)
     * @param vertices the vertex data to use (not null, unaffected)
     * @return a new instance
     */
    public static Mesh newInstance(Topology topology, List<Vertex> vertices) {
        Validate.nonNull(topology, "topology");

        int count = vertices.size();
        List<Integer> tempIndices = new ArrayList<>(count);
        List<Vertex> tempVertices = new ArrayList<>(count);
        Map<Vertex, Integer> tempMap = new HashMap<>(count);

        for (Vertex vertex : vertices) {
            Integer index = tempMap.get(vertex);
            if (index == null) {
                int nextIndex = tempVertices.size();
                tempIndices.add(nextIndex);
                tempVertices.add(vertex);
                tempMap.put(vertex, nextIndex);
            } else { // reuse a vertex we've already seen
                tempIndices.add(index);
            }
        }

        Mesh result = new Mesh(topology, tempIndices, tempVertices);
        return result;
    }

    /**
     * Apply the specified rotation to all vertices.
     *
     * @param xAngle the X rotation angle (in radians)
     * @param yAngle the Y rotation angle (in radians)
     * @param zAngle the Z rotation angle (in radians)
     * @return the (modified) current instance (for chaining)
     */
    public Mesh rotate(float xAngle, float yAngle, float zAngle) {
        if (xAngle == 0f && yAngle == 0f && zAngle == 0f) {
            return this;
        }
        verifyMutable();

        Quaternion quaternion // TODO garbage
                = new Quaternion().fromAngles(xAngle, yAngle, zAngle);

        positionBuffer.rotate(quaternion);
        if (normalBuffer != null) {
            normalBuffer.rotate(quaternion);
        }

        return this;
    }

    /**
     * Alter the primitive topology, which determines how vertices/indices are
     * organized into primitives.
     *
     * @param desiredTopology the desired enum value (not null)
     */
    public void setTopology(Topology desiredTopology) {
        verifyMutable();
        this.topology = desiredTopology;
    }

    /**
     * Return the primitive topology, which indicates how mesh vertices/indices
     * are organized into primitives.
     *
     * @return an enum value (not null)
     */
    public Topology topology() {
        assert topology != null;
        return topology;
    }

    /**
     * Transform all texture coordinates using the specified coefficients. Note
     * that the Z components of the coefficients are currently unused.
     *
     * @param uCoefficients the coefficients for calculating new Us (not null,
     * unaffected)
     * @param vCoefficients the coefficients for calculating new Vs (not null,
     * unaffected)
     * @return the (modified) current instance (for chaining)
     */
    public Mesh transformUvs(Vector4fc uCoefficients, Vector4fc vCoefficients) {
        if (texCoordsBuffer == null) {
            throw new IllegalStateException("There are no UVs in the mesh.");
        }
        Vector2f tmpVector = new Vector2f();
        verifyMutable();

        for (int vIndex = 0; vIndex < vertexCount; ++vIndex) {
            texCoordsBuffer.get2f(vIndex, tmpVector);

            float newU = uCoefficients.w()
                    + uCoefficients.x() * tmpVector.x
                    + uCoefficients.y() * tmpVector.y;
            float newV = vCoefficients.w()
                    + vCoefficients.x() * tmpVector.x
                    + vCoefficients.y() * tmpVector.y;
            tmpVector.set(newU, newV);

            texCoordsBuffer.put2f(vIndex, tmpVector);
        }

        return this;
    }
    // *************************************************************************
    // new protected methods

    /**
     * Replace any existing index buffer with a new one containing the specified
     * indices.
     *
     * @param indices the desired vertex indices (not null, unaffected)
     * @return a new IndexBuffer with the specified capacity
     */
    protected IndexBuffer createIndices(List<Integer> indices) {
        verifyMutable();
        this.indexBuffer = IndexBuffer.newInstance(indices);
        return indexBuffer;
    }

    /**
     * Create a buffer for putting vertex normals.
     *
     * @return a new buffer with a capacity of 3 * vertexCount floats
     */
    protected VertexBuffer createNormals() {
        verifyMutable();
        this.normalBuffer = VertexBuffer.newInstance(numAxes, vertexCount);
        return normalBuffer;
    }

    /**
     * Create a buffer for putting vertex positions.
     *
     * @return a new buffer with a capacity of 3 * vertexCount floats
     */
    protected VertexBuffer createPositions() {
        verifyMutable();
        this.positionBuffer = VertexBuffer.newInstance(numAxes, vertexCount);
        return positionBuffer;
    }

    /**
     * Create a buffer for putting vertex texture coordinates.
     *
     * @return a new buffer with a capacity of 2 * vertexCount floats
     */
    protected VertexBuffer createUvs() {
        verifyMutable();
        this.texCoordsBuffer = VertexBuffer.newInstance(2, vertexCount);
        return texCoordsBuffer;
    }

    /**
     * Assign new vertex indices.
     *
     * @param indexArray the vertex indices to use (not null, unaffected)
     */
    protected void setIndices(int... indexArray) {
        verifyMutable();
        this.indexBuffer = IndexBuffer.newInstance(indexArray);
    }

    /**
     * Assign new normals to the vertices.
     *
     * @param normalArray the desired vertex normals (not null,
     * length=3*vertexCount, unaffected)
     */
    protected void setNormals(float... normalArray) {
        int numFloats = normalArray.length;
        Validate.require(numFloats == vertexCount * numAxes, "correct length");
        verifyMutable();

        this.normalBuffer = VertexBuffer.newInstance(numAxes, normalArray);
    }

    /**
     * Assign new positions to the vertices.
     *
     * @param positionArray the desired vertex positions (not null,
     * length=3*vertexCount, unaffected)
     */
    protected void setPositions(float... positionArray) {
        int numFloats = positionArray.length;
        Validate.require(numFloats == vertexCount * numAxes, "correct length");
        verifyMutable();

        this.positionBuffer = VertexBuffer.newInstance(numAxes, positionArray);
    }

    /**
     * Assign new texture coordinates to the vertices.
     *
     * @param uvArray the desired vertex texture coordinates (not null,
     * length=2*vertexCount, unaffected)
     */
    protected void setUvs(float... uvArray) {
        int numFloats = uvArray.length;
        Validate.require(numFloats == 2 * vertexCount, "correct length");
        verifyMutable();

        this.texCoordsBuffer = VertexBuffer.newInstance(2, uvArray);
    }
    // *************************************************************************
    // jme3utilities.lbj.Mesh methods

    /**
     * Access the index buffer.
     *
     * @return the pre-existing instance (not null)
     */
    @Override
    public IndexBuffer getIndexBuffer() {
        assert indexBuffer != null;
        return indexBuffer;
    }

    /**
     * Access the normals data buffer.
     *
     * @return the pre-existing buffer (not null)
     */
    @Override
    public FloatBuffer getNormalsData() {
        return normalBuffer.getData();
    }

    /**
     * Access the positions data buffer for writing.
     *
     * @return the pre-existing buffer (not null)
     */
    @Override
    public FloatBuffer getPositionsData() {
        return positionBuffer.getData();
    }

    /**
     * Test whether the topology is LineList. Indexing is ignored.
     *
     * @return true if pure lines, otherwise false
     */
    @Override
    public boolean isPureLines() {
        boolean result = (topology == Topology.LineList);
        return result;
    }

    /**
     * Test whether the topology is TriangleList. Indexing is ignored.
     *
     * @return true if pure triangles, otherwise false
     */
    @Override
    public boolean isPureTriangles() {
        boolean result = (topology == Topology.TriangleList);
        return result;
    }

    /**
     * Indicate that the normals data have changed.
     */
    @Override
    public void setNormalsModified() {
        // TODO
    }

    /**
     * Indicate that the positions data have changed.
     */
    @Override
    public void setPositionsModified() {
        // TODO
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent the mesh as a text string.
     *
     * @return a descriptive string of text (not null)
     */
    @Override
    public String toString() {
        // Determine how many vertices to describe:
        int numToDescribe = countIndexedVertices();
        if (numToDescribe > 12) {
            numToDescribe = 12;
        }

        StringBuilder result = new StringBuilder(80 * (1 + numToDescribe));
        if (indexBuffer == null) {
            result.append("non");
        } else {
            int indexType = indexBuffer.indexType();
            String elementString = Utils.describeIndexType(indexType);
            result.append(elementString);
        }
        result.append("-indexed ");
        result.append(topology);
        result.append(" mesh (");
        result.append(vertexCount);
        if (vertexCount == 1) {
            result.append(" vertex");
        } else {
            result.append(" vertices");
        }

        if (indexBuffer != null) {
            result.append(", ");
            int numIndices = indexBuffer.capacity();
            result.append(numIndices);
            if (numIndices == 1) {
                result.append(" index");
            } else {
                result.append(" indices");
            }
        }

        int numTriangles = countTriangles();
        if (numTriangles > 0) {
            result.append(", ");
            result.append(numTriangles);
            result.append(" triangle");
            if (numTriangles != 1) {
                result.append("s");
            }
        }

        int numLines = countLines();
        if (numLines > 0) {
            result.append(", ");
            result.append(numLines);
            result.append(" line");
            if (numLines != 1) {
                result.append("s");
            }
        }
        result.append(")");
        String nl = System.lineSeparator();
        result.append(nl);
        /*
         * In the body of the description, vertices are grouped into primitives,
         * separated by empty lines.
         */
        int vpp = topology.vpp();
        int numShared = topology.numShared();

        for (int i = 0; i < numToDescribe; ++i) {
            if (i >= vpp && ((i - numShared) % (vpp - numShared)) == 0) {
                result.append(nl);
            }

            int vertexIndex = (indexBuffer == null) ? i : indexBuffer.get(i);
            result.append(vertexIndex);
            result.append(": ");
            Vertex v = copyVertex(vertexIndex);
            result.append(v);
            result.append(nl);
        }
        if (countIndexedVertices() > numToDescribe) {
            result.append("...");
            result.append(nl);
        }

        return result.toString();
    }
    // *************************************************************************
    // private methods

    /**
     * Verify that the mesh is still mutable.
     */
    private void verifyMutable() {
        if (!mutable) {
            throw new IllegalStateException("The mesh is no longer mutable.");
        }
    }
}
