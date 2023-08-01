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
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;

/**
 * Encapsulate the handles of a Vulkan buffer resource, such as an index buffer,
 * uniform buffer, or vertex buffer.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class BufferResource {
    // *************************************************************************
    // fields

    /**
     * mapped data buffer, or null if none
     */
    private ByteBuffer data;
    /**
     * handle of the buffer resource
     */
    private long bufferHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the buffer's memory
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a buffer resource.
     *
     * @param numBytes the desired size in bytes (&ge;0)
     * @param usage a bitmask specifying the intended usage (index, uniform,
     * vertex, etcetera)
     * @param staging true to use a staging buffer and invoke the {@code fill{}}
     * method, false for a persistent mapping and no staging buffer
     */
    BufferResource(int numBytes, int usage, boolean staging) {
        VkDevice logicalDevice = BaseApplication.getLogicalDevice();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBufferHandle = stack.mallocLong(1);
            LongBuffer pMemoryHandle = stack.mallocLong(1);
            PointerBuffer pPointer = stack.mallocPointer(1);
            int properties;

            if (staging) {
                // Create a temporary buffer object for staging:
                int createUsage = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
                properties = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
                BaseApplication.createBuffer(numBytes, createUsage,
                        properties, pBufferHandle, pMemoryHandle);
                long stagingBufferHandle = pBufferHandle.get(0);
                long stagingMemoryHandle = pMemoryHandle.get(0);

                // Temporarily map the staging buffer's memory:
                int offset = 0;
                int flags = 0x0;
                int retCode = VK10.vkMapMemory(logicalDevice,
                        stagingMemoryHandle, offset, numBytes, flags, pPointer);
                Utils.checkForError(retCode, "map staging buffer's memory");

                int index = 0; // the index within pPointer
                this.data = pPointer.getByteBuffer(index, numBytes);
                fill(data);
                this.data = null;
                VK10.vkUnmapMemory(logicalDevice, stagingMemoryHandle);
                /*
                 * Create a device-local buffer that's optimized for being a
                 * copy destination:
                 */
                createUsage = usage | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
                properties = VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
                BaseApplication.createBuffer(numBytes, createUsage,
                        properties, pBufferHandle, pMemoryHandle);
                this.bufferHandle = pBufferHandle.get(0);
                this.memoryHandle = pMemoryHandle.get(0);

                BaseApplication.copyBuffer(
                        stagingBufferHandle, bufferHandle, numBytes);

                // Destroy the staging buffer and free its memory:
                VkAllocationCallbacks allocator = BaseApplication.allocator();
                VK10.vkDestroyBuffer(
                        logicalDevice, stagingBufferHandle, allocator);
                VK10.vkFreeMemory(
                        logicalDevice, stagingMemoryHandle, allocator);

            } else {
                properties = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                        | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
                BaseApplication.createBuffer(numBytes, usage,
                        properties, pBufferHandle, pMemoryHandle);
                this.bufferHandle = pBufferHandle.get(0);
                this.memoryHandle = pMemoryHandle.get(0);

                // Persistently map the buffer's memory:
                int offset = 0;
                int flags = 0x0;
                int retCode = VK10.vkMapMemory(logicalDevice, memoryHandle,
                        offset, numBytes, flags, pPointer);
                Utils.checkForError(retCode, "map buffer's memory");

                int index = 0;
                this.data = pPointer.getByteBuffer(index, numBytes);
                fill(data);
            }
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy the buffer, if it has been created.
     */
    void destroy() {
        VkDevice logicalDevice = BaseApplication.getLogicalDevice();
        VkAllocationCallbacks allocator = BaseApplication.allocator();

        if (bufferHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(logicalDevice, bufferHandle, allocator);
            this.bufferHandle = VK10.VK_NULL_HANDLE;
        }

        if (memoryHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(logicalDevice, memoryHandle, allocator);
            this.memoryHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Access the data buffer.
     *
     * @return the pre-existing buffer, or null if not persistent
     */
    final ByteBuffer getData() {
        return data;
    }

    /**
     * Return the buffer's handle.
     *
     * @return the handle
     */
    final long handle() {
        return bufferHandle;
    }
    // *************************************************************************
    // new protected methods

    /**
     * Fill the buffer's memory with data during instantiation with
     * {@code staging=true}. Meant to be overridden.
     *
     * @param destinationBuffer the pre-existing mapped data buffer
     */
    protected void fill(ByteBuffer destinationBuffer) {
        // do nothing
    }
}
