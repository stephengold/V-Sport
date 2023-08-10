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

import jme3utilities.Validate;
import org.lwjgl.vulkan.VK10;

/**
 * A VkBuffer and its associated device memory, both allocated from a specific
 * LogicalDevice.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class MappableBuffer {
    // *************************************************************************
    // fields

    /**
     * size of the buffer (in bytes, &gt;0)
     */
    final private int numBytes;
    /**
     * handle of the associated {@code VkDeviceMemory}, or null if none
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the underlying {@code VkBuffer}
     */
    private long vkBufferHandle;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a mappable buffer without any associated memory.
     *
     * @param numBytes the size of the buffer (in bytes, &gt;0)
     * @param bufferHandle the handle of the {@code VkBuffer} (not null)
     */
    MappableBuffer(int numBytes, long bufferHandle) {
        Validate.positive(numBytes, "number of bytes");
        Validate.nonZero(bufferHandle, "buffer handle");

        this.numBytes = numBytes;
        this.vkBufferHandle = bufferHandle;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Associate the specified memory with this buffer.
     *
     * @param handle the handle of the {@code VkDeviceMemory} to associate (not
     * null)
     */
    void associateWithMemory(long handle) {
        Validate.nonZero(handle, "handle");
        if (memoryHandle != VK10.VK_NULL_HANDLE) {
            throw new IllegalStateException("already associated with "
                    + Long.toHexString(memoryHandle));
        }

        this.memoryHandle = handle;
    }

    /**
     * Destroy the buffer and free its memory.
     *
     * @return null
     */
    MappableBuffer destroy() {
        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        this.vkBufferHandle = logicalDevice.destroyBuffer(vkBufferHandle);
        this.memoryHandle = logicalDevice.freeMemory(memoryHandle);

        return null;
    }

    /**
     * Access the associated memory, if any.
     *
     * @return the handle of the pre-existing {@code VkDeviceMemory}, or null if
     * none set
     */
    long memoryHandle() {
        return memoryHandle;
    }

    /**
     * Return the capacity of the buffer, in bytes.
     *
     * @return the capacity (&gt;0)
     */
    int numBytes() {
        assert numBytes > 0 : numBytes;
        return numBytes;
    }

    /**
     * Access the underlying {@code VkBuffer}.
     *
     * @return the handle of the pre-existing instance (not null)
     */
    long vkBufferHandle() {
        assert vkBufferHandle != VK10.VK_NULL_HANDLE;
        return vkBufferHandle;
    }
}
