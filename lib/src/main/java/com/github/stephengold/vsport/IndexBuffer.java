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

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import org.lwjgl.vulkan.VK10;

/**
 * Wrapper class for the index buffer of a V-Sport mesh, including its
 * BufferResource.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class IndexBuffer extends jme3utilities.lbj.IndexBuffer {
    // *************************************************************************
    // fields

    /**
     * buffer resource
     */
    private BufferResource bufferResource;
    /**
     * Vulkan data type of the individual indices (either
     * {@code VK_INDEX_TYPE_UINT16} or {@code VK_INDEX_TYPE_UINT32})
     */
    final private int indexType;
    // *************************************************************************
    // constructors

    /**
     * Instantiate an IndexBuffer by wrapping the specified Buffer.
     *
     * @param data the data buffer to wrap (either a {@code ShortBuffer} or a
     * {@code IntBuffer}, alias created)
     * @param indexType either {@code VK_INDEX_TYPE_UINT16} or
     * {@code VK_INDEX_TYPE_UINT32}
     * @param bufferResource (not null, alias created)
     */
    private IndexBuffer(
            Buffer data, int indexType, BufferResource bufferResource) {
        super(data);

        assert indexType == VK10.VK_INDEX_TYPE_UINT16
                || indexType == VK10.VK_INDEX_TYPE_UINT32 : indexType;
        this.indexType = indexType;

        assert bufferResource != null;
        this.bufferResource = bufferResource;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the buffer's capacity.
     *
     * @return the element count (&ge;0)
     */
    public int capacity() {
        int result = getBuffer().capacity();
        return result;
    }

    /**
     * Destroy the buffer resource.
     */
    void destroy() {
        if (bufferResource != null) {
            bufferResource.destroy();
            this.bufferResource = null;
        }
    }

    /**
     * Flip the buffer. The limit is set to the current read/write position, and
     * then the read/write position is zeroed. The data in the buffer is
     * unaffected.
     *
     * @return the (modified) current instance (for chaining)
     */
    public IndexBuffer flip() {
        getBuffer().flip();
        return this;
    }

    /**
     * Access the underlying {@code VkBuffer}.
     *
     * @return the handle (not null)
     */
    long handle() {
        long result = bufferResource.handle();
        return result;
    }

    /**
     * Return the type of vertex indices (elements) contained in the buffer.
     *
     * @return either {@code VK_INDEX_TYPE_UINT16} or
     * {@code VK_INDEX_TYPE_UINT32}
     */
    int indexType() {
        return indexType;
    }

    /**
     * Return the buffer's limit.
     *
     * @return the limit position (&ge;0, &le;capacity)
     */
    public int limit() {
        int result = getBuffer().limit();
        return result;
    }

    /**
     * Create an index buffer without initializing its contents.
     *
     * @param maxVertices one more than the highest index value (&ge;0)
     * @param capacity the desired number of indices (&ge;0)
     * @return a new instance (not null)
     */
    static IndexBuffer newInstance(int maxVertices, int capacity) {
        boolean staging = false;

        BufferResource resource;
        int elementType;
        Buffer data;
        if (maxVertices > (1 << 16)) {
            elementType = VK10.VK_INDEX_TYPE_UINT32;
            int numBytes = capacity * Integer.BYTES;
            resource = new BufferResource(
                    numBytes, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, staging);
            ByteBuffer bytes = resource.findData();
            bytes.position(numBytes);
            bytes.flip();
            data = bytes.asIntBuffer();

        } else { // Use 16-bit indices to conserve memory:
            elementType = VK10.VK_INDEX_TYPE_UINT16;
            int numBytes = capacity * Short.BYTES;
            resource = new BufferResource(
                    numBytes, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, staging);
            ByteBuffer bytes = resource.findData();
            bytes.position(numBytes);
            bytes.flip();
            data = bytes.asShortBuffer();
        }

        IndexBuffer result = new IndexBuffer(data, elementType, resource);
        return result;
    }

    /**
     * Create an IndexBuffer from an array of vertex indices.
     *
     * @param indices the desired indices (not null, unaffected)
     * @return a new instance (not null)
     */
    static IndexBuffer newInstance(int[] indices) {
        int capacity = indices.length;
        int maxIndex = Utils.maxInt(indices);
        int maxVertices = 1 + maxIndex;

        IndexBuffer result = newInstance(maxVertices, capacity);
        for (int vIndex : indices) {
            result.put(vIndex);
        }

        return result;
    }

    /**
     * Create an IndexBuffer from a list of indices.
     *
     * @param indices the desired indices (not null, unaffected)
     * @return a new instance (not null)
     */
    static IndexBuffer newInstance(Collection<Integer> indices) {
        int capacity = indices.size();
        int maxIndex = Collections.max(indices);
        int maxVertices = 1 + maxIndex;

        IndexBuffer result = newInstance(maxVertices, capacity);
        for (int vIndex : indices) {
            result.put(vIndex);
        }

        return result;
    }
    // *************************************************************************
    // jme3utilities.lbj.IndexBuffer methods

    /**
     * Make the buffer immutable.
     *
     * @return the (modified) current instance (for chaining)
     */
    @Override
    public IndexBuffer makeImmutable() {
        super.makeImmutable();
        return this;
    }

    /**
     * Write the specified index at the current read/write position, then
     * increment the position.
     *
     * @param index the index to be written (&ge;0, &lt;numVertices)
     * @return the (modified) current instance (for chaining)
     */
    @Override
    public IndexBuffer put(int index) {
        super.put(index);
        return this;
    }
}
