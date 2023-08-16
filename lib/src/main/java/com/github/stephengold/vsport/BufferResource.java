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
import org.lwjgl.vulkan.VK10;

/**
 * A buffer resource that persists across device changes, such as an index
 * buffer, uniform buffer object (UBO), or vertex buffer.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class BufferResource extends DeviceResource {
    // *************************************************************************
    // fields

    /**
     * true to use a staging buffer during creation, false to create a
     * persistent mapping
     */
    final private boolean staging;
    /**
     * mapped data buffer, or null if none
     */
    private ByteBuffer data;
    /**
     * buffer size in bytes (&ge;0)
     */
    final private int numBytes;
    /**
     * bitmask specifying the intended usage (index buffer, UBO, vertex buffer,
     * etcetera)
     */
    final private int usage;
    /**
     * underlying VkBuffer and VkDeviceMemory
     */
    private MappableBuffer mappableBuffer = null;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a buffer resource.
     *
     * @param numBytes the desired size in bytes (&ge;0)
     * @param usage a bitmask specifying the intended usage (index buffer, UBO,
     * vertex buffer, etcetera)
     * @param staging true to use a staging buffer during creation, false for a
     * persistent mapping
     */
    BufferResource(int numBytes, int usage, boolean staging) {
        this.numBytes = numBytes;
        this.usage = usage;
        this.staging = staging;

        create();
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Access the mapped data.
     *
     * @return the pre-existing NIO buffer, or null if not mapped
     */
    final ByteBuffer getData() {
        return data;
    }

    /**
     * Access the underlying {@code VkBuffer}.
     *
     * @return the handle of the pre-existing instance (not null)
     */
    final long handle() {
        long result = mappableBuffer.vkBufferHandle();
        return result;
    }
    // *************************************************************************
    // new protected methods

    /**
     * Fill the buffer's memory with data during creation. Meant to be
     * overridden.
     *
     * @param destinationBuffer the pre-existing mapped data buffer
     */
    protected void fill(ByteBuffer destinationBuffer) {
        // do nothing
    }
    // *************************************************************************
    // DeviceResource methods

    /**
     * Unmap and destroy the mappable buffer, if it exists.
     */
    @Override
    protected void destroy() {
        this.data = null;
        if (mappableBuffer != null) {
            this.mappableBuffer = mappableBuffer.destroy();
        }

        super.destroy();
    }

    /**
     * Update this object after a device change.
     *
     * @param nextDevice the current device if it's just been created, or null
     * if the current device is about to be destroyed
     */
    @Override
    void updateLogicalDevice(LogicalDevice nextDevice) {
        this.data = null;
        if (mappableBuffer != null) {
            this.mappableBuffer = mappableBuffer.destroy();
        }

        if (nextDevice != null) {
            create();
        }
    }
    // *************************************************************************
    // private methods

    /**
     * Copy the contents of one buffer object to another.
     *
     * @param source the source buffer (not null)
     * @param destination the destination buffer (not null, same capacity as
     * source)
     */
    private static void copyBuffer(
            MappableBuffer source, MappableBuffer destination) {
        Validate.nonNull(source, "source");
        Validate.nonNull(destination, "destination");
        assert destination.numBytes() == source.numBytes();

        SingleUse commandSequence = new SingleUse();
        commandSequence.addCopyBufferToBuffer(source, destination);
        commandSequence.submitToGraphicsQueue();
    }

    /**
     * Create the underlying resources.
     */
    private void create() {
        if (staging) {
            createByStaging();
        } else {
            createWithPersistentMapping();
        }
    }

    private void createByStaging() {
        // Create a temporary MappableBuffer for staging:
        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        int createUsage = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        int properties = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        MappableBuffer stagingBuffer = logicalDevice.createMappable(
                numBytes, createUsage, properties);

        // Briefly map the staging buffer's memory and fill it with data:
        this.data = logicalDevice.map(stagingBuffer);
        fill(data);
        this.data = null;
        logicalDevice.unmap(stagingBuffer);
        /*
         * Create a device-local MappableBuffer that's optimized for being a
         * copy destination:
         */
        createUsage = usage | VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
        properties = VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT;
        this.mappableBuffer = logicalDevice.createMappable(
                numBytes, createUsage, properties);

        copyBuffer(stagingBuffer, mappableBuffer);

        // Destroy the staging buffer and free its memory:
        stagingBuffer.destroy();
    }

    private void createWithPersistentMapping() {
        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        int properties = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        this.mappableBuffer = logicalDevice.createMappable(
                numBytes, usage, properties);

        // Persistently map the buffer's memory and fill it with data:
        this.data = logicalDevice.map(mappableBuffer);
        fill(data);
    }
}
