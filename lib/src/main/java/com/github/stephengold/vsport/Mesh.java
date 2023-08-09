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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

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
     * number of vertices per triangle
     */
    final public static int vpt = 3;
    // *************************************************************************
    // fields

    /**
     * vertex colors (3 floats per vertex) or null if not present
     */
    private BufferResource colorBuffer;
    /**
     * vertex normals (3 floats per vertex) or null if not present
     */
    private BufferResource normalBuffer;
    /**
     * vertex positions (3 floats per vertex)
     */
    private BufferResource positionBuffer;
    /**
     * texture coordinates (2 floats per vertex) or null if not present
     */
    private BufferResource texCoordsBuffer;
    /**
     * vertex colors (3 floats per vertex) or null if none
     */
    private FloatBuffer colorFloats;
    /**
     * vertex normals (3 floats per vertex) or null if none
     */
    private FloatBuffer normalFloats;
    /**
     * vertex positions (3 floats per vertex)
     */
    private FloatBuffer positionFloats;
    /**
     * vertex texture coordinates (2 floats per vertex) or null if none
     */
    private FloatBuffer texCoordsFloats;
    /**
     * vertex indices, or null if none
     */
    private IndexBuffer indexBuffer;
    /**
     * number of vertices (based on buffer sizes, unmodified by indexing)
     */
    final private int vertexCount;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a mesh from vertices and optional indices.
     *
     * @param indices the vertex indices to use (unaffected) or null if none
     * @param vertices the vertex data to use (not null, unaffected)
     */
    Mesh(List<Integer> indices, List<Vertex> vertices) {
        if (indices == null) {
            this.indexBuffer = null;
        } else {
            this.indexBuffer = IndexBuffer.newInstance(indices);
        }

        this.vertexCount = vertices.size();
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;

        // position buffer:
        int numBytes = vertexCount * numAxes * Float.BYTES;
        this.positionBuffer = new BufferResource(numBytes, usage, staging) {
            @Override
            protected void fill(ByteBuffer destinationBuffer) {
                for (Vertex vertex : vertices) {
                    vertex.writePositionTo(destinationBuffer);
                }
            }
        };

        ByteBuffer byteBuffer = positionBuffer.getData();
        byteBuffer.flip();
        this.positionFloats = byteBuffer.asFloatBuffer();

        // normal buffer:
        Vertex representativeVertex = vertices.get(0);
        boolean hasNormal = representativeVertex.hasNormal();
        if (hasNormal) {
            numBytes = vertexCount * numAxes * Float.BYTES;
            this.normalBuffer = new BufferResource(numBytes, usage, staging) {
                @Override
                protected void fill(ByteBuffer destinationBuffer) {
                    for (Vertex vertex : vertices) {
                        vertex.writeNormalTo(destinationBuffer);
                    }
                }
            };

            byteBuffer = normalBuffer.getData();
            byteBuffer.flip();
            this.normalFloats = byteBuffer.asFloatBuffer();

        } else {
            this.normalBuffer = null;
            this.normalFloats = null;
        }

        // texture-coordinates buffer:
        boolean hasTexCoords = representativeVertex.hasTexCoords();
        if (hasTexCoords) {
            numBytes = vertexCount * 2 * Float.BYTES;
            this.texCoordsBuffer = new BufferResource(
                    numBytes, usage, staging) {
                @Override
                protected void fill(ByteBuffer destinationBuffer) {
                    for (Vertex vertex : vertices) {
                        vertex.writeTexCoordsTo(destinationBuffer);
                    }
                }
            };

            byteBuffer = texCoordsBuffer.getData();
            byteBuffer.flip();
            this.texCoordsFloats = byteBuffer.asFloatBuffer();

        } else {
            this.texCoordsBuffer = null;
            this.texCoordsFloats = null;
        }

        // color buffer:
        boolean hasColor = representativeVertex.hasColor();
        if (hasColor) {
            numBytes = vertexCount * 3 * Float.BYTES;
            this.colorBuffer = new BufferResource(numBytes, usage, staging) {
                @Override
                protected void fill(ByteBuffer destinationBuffer) {
                    for (Vertex vertex : vertices) {
                        vertex.writeColorTo(destinationBuffer);
                    }
                }
            };

            byteBuffer = colorBuffer.getData();
            byteBuffer.flip();
            this.colorFloats = byteBuffer.asFloatBuffer();

        } else {
            this.colorBuffer = null;
            this.colorFloats = null;
        }
    }

    /**
     * Instantiate a mesh with the specified number of vertices, but no indices,
     * positions, colors, normals, or texture coordinates.
     *
     * @param vertexCount number of vertices (&ge;0)
     */
    protected Mesh(int vertexCount) {
        Validate.nonNegative(vertexCount, "vertex count");
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
        float xPos = positionFloats.get(numAxes * vertexIndex);
        float yPos = positionFloats.get(numAxes * vertexIndex + 1);
        float zPos = positionFloats.get(numAxes * vertexIndex + 2);
        Vector3fc position = new Vector3f(xPos, yPos, zPos);

        Vector3fc color = null;
        if (colorFloats != null) {
            float red = colorFloats.get(3 * vertexIndex);
            float green = colorFloats.get(3 * vertexIndex + 1);
            float blue = colorFloats.get(3 * vertexIndex + 2);
            color = new Vector3f(red, green, blue);
        }

        Vector3fc normal = null;
        if (normalFloats != null) {
            float x = normalFloats.get(numAxes * vertexIndex);
            float y = normalFloats.get(numAxes * vertexIndex + 1);
            float z = normalFloats.get(numAxes * vertexIndex + 2);
            normal = new Vector3f(x, y, z);
        }

        Vector2fc texCoords = null;
        if (texCoordsFloats != null) {
            float u = texCoordsFloats.get(2 * vertexIndex);
            float v = texCoordsFloats.get(2 * vertexIndex + 1);
            texCoords = new Vector2f(u, v);
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
     * Count how many vertices the mesh renders, taking indexing into account.
     *
     * @return the count (&ge;0)
     */
    int countIndexedVertices() {
        int result = (indexBuffer == null) ? vertexCount : indexBuffer.size();
        return result;
    }

    /**
     * Count how many vertices the mesh contains, based on VertexBuffer
     * capacities, unmodified by draw mode and indexing.
     *
     * @return the count (&ge;0)
     */
    public int countVertices() {
        return vertexCount;
    }

    /**
     * Remove the normals from the mesh.
     */
    public void dropNormals() {
        this.normalBuffer = null;
        this.normalFloats = null;
    }

    /**
     * Remove the texture coordinates, if any.
     */
    public void dropTexCoords() {
        this.texCoordsBuffer = null;
        this.texCoordsFloats = null;
    }

    /**
     * Generate an attribute-description buffer.
     *
     * @param stack for memory allocation (not null)
     * @return a new temporary buffer
     */
    VkVertexInputAttributeDescription.Buffer
            generateAttributeDescriptions(MemoryStack stack) {
        int numAttributes = countAttributes();
        VkVertexInputAttributeDescription.Buffer result
                = VkVertexInputAttributeDescription.calloc(
                        numAttributes, stack);

        int slotIndex = 0; // current binding/slot
        int offset = 0;

        // position attribute (3 signed floats in slot 0)
        VkVertexInputAttributeDescription posDescription
                = result.get(slotIndex);
        posDescription.binding(slotIndex);
        posDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
        posDescription.location(slotIndex); // slot 0 (see the vertex shader)
        posDescription.offset(offset); // start offset in bytes

        if (colorBuffer != null) {
            // color attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription colorDescription
                    = result.get(slotIndex);
            colorDescription.binding(slotIndex);
            colorDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
            colorDescription.location(slotIndex);
            colorDescription.offset(offset); // start offset in bytes
        }

        if (normalBuffer != null) {
            // normal attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription normalDescription
                    = result.get(slotIndex);
            normalDescription.binding(slotIndex);
            normalDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
            normalDescription.location(slotIndex);
            normalDescription.offset(offset); // start offset in bytes
        }

        if (texCoordsBuffer != null) {
            // texCoords attribute (2 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription texCoordsDescription
                    = result.get(slotIndex);
            texCoordsDescription.binding(slotIndex);
            texCoordsDescription.format(VK10.VK_FORMAT_R32G32_SFLOAT);
            texCoordsDescription.location(slotIndex);
            texCoordsDescription.offset(offset); // start offset in bytes
        }

        return result;
    }

    /**
     * Generate a binding-description buffer.
     *
     * @param stack for memory allocation (not null)
     * @return a new temporary buffer
     */
    VkVertexInputBindingDescription.Buffer
            generateBindingDescription(MemoryStack stack) {
        int numAttributes = countAttributes();
        VkVertexInputBindingDescription.Buffer result
                = VkVertexInputBindingDescription.calloc(numAttributes, stack);

        int slotIndex = 0; // current binding/slot

        // position attribute (3 signed floats in slot 0)
        VkVertexInputBindingDescription posDescription = result.get(0);
        posDescription.binding(slotIndex);
        posDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        posDescription.stride(numAxes * Float.BYTES);

        if (colorBuffer != null) {
            // color attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription colorDescription
                    = result.get(slotIndex);
            colorDescription.binding(slotIndex);
            colorDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            colorDescription.stride(3 * Float.BYTES);
        }

        if (normalBuffer != null) {
            // normal attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription normalDescription
                    = result.get(slotIndex);
            normalDescription.binding(slotIndex);
            normalDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            normalDescription.stride(numAxes * Float.BYTES);
        }

        if (texCoordsBuffer != null) {
            // texture-coordinates attribute (2 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription tcDescription
                    = result.get(slotIndex);
            tcDescription.binding(slotIndex);
            tcDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            tcDescription.stride(2 * Float.BYTES);
        }

        return result;
    }

    /**
     * Generate buffer handles for the BindVertexBuffers command.
     *
     * @param stack for memory allocation (not null)
     * @return a new temporary buffer
     */
    LongBuffer generateBufferHandles(MemoryStack stack) {
        int numAttributes = countAttributes();
        LongBuffer result = stack.mallocLong(numAttributes);

        int slotIndex = 0;
        result.put(slotIndex, positionBuffer.handle());
        if (colorBuffer != null) {
            ++slotIndex;
            result.put(slotIndex, colorBuffer.handle());
        }
        if (normalBuffer != null) {
            ++slotIndex;
            result.put(slotIndex, normalBuffer.handle());
        }
        if (texCoordsBuffer != null) {
            ++slotIndex;
            result.put(slotIndex, texCoordsBuffer.handle());
        }

        return result;
    }

    /**
     * Test whether this Mesh is indexed.
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
     * Create a mesh by de-duplicating a list of vertices.
     *
     * @param vertices the vertex data to use (not null, unaffected)
     * @return a new instance
     */
    public static Mesh newInstance(List<Vertex> vertices) {
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

        Mesh result = new Mesh(tempIndices, tempVertices);
        return result;
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
        this.indexBuffer = IndexBuffer.newInstance(indices);
        return indexBuffer;
    }

    /**
     * Create a buffer for putting vertex normals.
     *
     * @return a new buffer with a capacity of 3 * vertexCount floats
     */
    protected FloatBuffer createNormals() {
        int numBytes = vertexCount * 3 * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.normalBuffer = new BufferResource(numBytes, usage, staging);

        ByteBuffer byteBuffer = normalBuffer.getData();
        byteBuffer.rewind();
        this.normalFloats = byteBuffer.asFloatBuffer();

        return normalFloats;
    }

    /**
     * Create a buffer for putting vertex positions.
     *
     * @return a new buffer with a capacity of 3 * vertexCount floats
     */
    protected FloatBuffer createPositions() {
        int numBytes = vertexCount * 3 * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.positionBuffer = new BufferResource(numBytes, usage, staging);

        ByteBuffer byteBuffer = positionBuffer.getData();
        byteBuffer.rewind();
        this.positionFloats = byteBuffer.asFloatBuffer();

        return positionFloats;
    }

    /**
     * Create a buffer for putting vertex texture coordinates.
     *
     * @return a new buffer with a capacity of 2 * vertexCount floats
     */
    protected FloatBuffer createUvs() {
        int numBytes = vertexCount * 2 * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.texCoordsBuffer = new BufferResource(numBytes, usage, staging);

        ByteBuffer byteBuffer = texCoordsBuffer.getData();
        byteBuffer.rewind();
        this.texCoordsFloats = byteBuffer.asFloatBuffer();

        return texCoordsFloats;
    }

    /**
     * Assign new vertex indices.
     *
     * @param indexArray the vertex indices to use (not null, unaffected)
     */
    protected void setIndices(int... indexArray) {
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

        int numBytes = numFloats * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.normalBuffer = new BufferResource(numBytes, usage, staging) {
            @Override
            protected void fill(ByteBuffer destinationBuffer) {
                for (int floatI = 0; floatI < numFloats; ++floatI) {
                    float fValue = normalArray[floatI];
                    destinationBuffer.putFloat(fValue);
                }
            }
        };

        ByteBuffer byteBuffer = normalBuffer.getData();
        byteBuffer.flip();
        this.normalFloats = byteBuffer.asFloatBuffer();
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

        int numBytes = numFloats * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.positionBuffer = new BufferResource(numBytes, usage, staging) {
            @Override
            protected void fill(ByteBuffer destinationBuffer) {
                for (int floatI = 0; floatI < numFloats; ++floatI) {
                    float fValue = positionArray[floatI];
                    destinationBuffer.putFloat(fValue);
                }
            }
        };

        ByteBuffer byteBuffer = positionBuffer.getData();
        byteBuffer.flip();
        this.positionFloats = byteBuffer.asFloatBuffer();
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

        int numBytes = numFloats * Float.BYTES;
        int usage = VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
        boolean staging = false;
        this.texCoordsBuffer = new BufferResource(numBytes, usage, staging) {
            @Override
            protected void fill(ByteBuffer destinationBuffer) {
                for (int floatI = 0; floatI < numFloats; ++floatI) {
                    float fValue = uvArray[floatI];
                    destinationBuffer.putFloat(fValue);
                }
            }
        };

        ByteBuffer byteBuffer = texCoordsBuffer.getData();
        byteBuffer.flip();
        this.texCoordsFloats = byteBuffer.asFloatBuffer();
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
        return normalFloats;
    }

    /**
     * Access the positions data buffer.
     *
     * @return the pre-existing buffer (not null)
     */
    @Override
    public FloatBuffer getPositionsData() {
        return positionFloats;
    }

    /**
     * Test whether the draw mode is GL_LINES. Indexing is allowed.
     *
     * @return true if pure lines, otherwise false
     */
    @Override
    public boolean isPureLines() {
        return false;
    }

    /**
     * Test whether the draw mode is GL_TRIANGLES.
     *
     * @return true if pure triangles, otherwise false
     */
    @Override
    public boolean isPureTriangles() {
        return true;
    }

    /**
     * Indicate that the normals data has changed.
     */
    @Override
    public void setNormalsModified() {
        // TODO
    }

    /**
     * Indicate that the positions data has changed.
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
        String nl = System.lineSeparator();

        int numRendered = countIndexedVertices();
        StringBuilder result = new StringBuilder(80 * numRendered);

        for (int i = 0; i < numRendered; ++i) {
            if ((i % vpt) == 0) {
                result.append(nl);
            }
            int vertexIndex = (indexBuffer == null) ? i : indexBuffer.get(i);
            result.append(vertexIndex);
            result.append(": ");
            Vertex v = copyVertex(vertexIndex);
            result.append(v);
            result.append(nl);
        }

        return result.toString();
    }
}
