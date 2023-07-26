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
import java.util.Collections;
import java.util.List;
import org.lwjgl.vulkan.VK10;

/**
 * Wrapper class for the index buffer of a V-Sport mesh, including its
 * BufferResource.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final class IndexBuffer extends jme3utilities.lbj.IndexBuffer {
    // *************************************************************************
    // fields

    /**
     * buffer resource
     */
    private BufferResource bufferResource;
    /**
     * Vulkan data type of the individual elements (either
     * {@code VK_INDEX_TYPE_UINT16} or {@code VK_INDEX_TYPE_UINT32})
     */
    final private int elementType;
    // *************************************************************************
    // constructors

    /**
     * Instantiate an IndexBuffer by wrapping the specified Buffer.
     *
     * @param data the data buffer to wrap (either a {@code ShortBuffer} or a
     * {@code IntBuffer}, alias created)
     * @param elementType either {@code VK_INDEX_TYPE_UINT16} or
     * {@code VK_INDEX_TYPE_UINT32}}
     * @param bufferResource (not null, alias created)
     */
    private IndexBuffer(
            Buffer data, int elementType, BufferResource bufferResource) {
        super(data);

        assert elementType == VK10.VK_INDEX_TYPE_UINT16
                || elementType == VK10.VK_INDEX_TYPE_UINT32 : elementType;
        this.elementType = elementType;

        assert bufferResource != null;
        this.bufferResource = bufferResource;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy all resources owned by this IndexBuffer.
     */
    void destroy() {
        if (bufferResource != null) {
            bufferResource.destroy();
            this.bufferResource = null;
        }
    }

    /**
     * Return the type of index.
     *
     * @return either
     */
    int elementType() {
        return elementType;
    }

    /**
     * Return the handle of the buffer resource.
     *
     * @return the handle
     */
    long handle() {
        long result = bufferResource.handle();
        return result;
    }

    /**
     * Create an IndexBuffer from a list of indices.
     *
     * @param indices the desired indices (not null, unaffected)
     * @return a new instance (not null)
     */
    static IndexBuffer newInstance(List<Integer> indices) {
        int numIndices = indices.size();
        boolean staging = false;
        int maxIndex = Collections.max(indices);
        int numVertices = 1 + maxIndex;

        BufferResource resource;
        int elementType;
        Buffer data;
        if (numVertices > (1 << 16)) {
            elementType = VK10.VK_INDEX_TYPE_UINT32;
            int numBytes = numIndices * Integer.BYTES;
            resource = new BufferResource(
                    numBytes, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, staging) {
                @Override
                void fill(ByteBuffer destinationBuffer) {
                    for (int vIndex : indices) {
                        destinationBuffer.putInt(vIndex);
                    }
                }
            };
            ByteBuffer bytes = resource.getData();
            bytes.flip();
            data = bytes.asIntBuffer();

        } else { // Use 16-bit indices to conserve memory:
            elementType = VK10.VK_INDEX_TYPE_UINT16;
            int numBytes = numIndices * Short.BYTES;
            resource = new BufferResource(
                    numBytes, VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, staging) {
                @Override
                void fill(ByteBuffer destinationBuffer) {
                    for (int vIndex : indices) {
                        destinationBuffer.putShort((short) vIndex);
                    }
                }
            };
            ByteBuffer bytes = resource.getData();
            bytes.flip();
            data = bytes.asShortBuffer();
        }

        IndexBuffer result = new IndexBuffer(data, elementType, resource);
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
}