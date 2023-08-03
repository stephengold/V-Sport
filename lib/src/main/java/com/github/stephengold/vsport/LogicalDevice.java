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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import jme3utilities.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkQueue;

/**
 * Encapsulate a Vulkan logical device, used to allocate command buffers,
 * images, and mappable buffers.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
public class LogicalDevice {
    // *************************************************************************
    // fields

    /**
     * all device-dependent resources
     */
    final static Set<DeviceResource> resourceSet = new TreeSet<>();
    /**
     * allocator for direct buffers
     */
    final VkAllocationCallbacks allocator;
    /**
     * underlying lwjgl-vulkan device
     */
    private VkDevice vkDevice;
    // *************************************************************************
    // constructors

    LogicalDevice(VkDevice vkDevice) {
        this.vkDevice = vkDevice;
        this.allocator = BaseApplication.findAllocator();

        for (DeviceResource resource : resourceSet) {
            resource.updateLogicalDevice(this);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Allocate a single command buffer from this device.
     *
     * @return the new instance (not null)
     */
    VkCommandBuffer allocateCommandBuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // TODO a pool of short-lived command buffers - see copyBuffer()
            PointerBuffer pPointer = allocateCommandBuffersInternal(1, stack);
            long pointer = pPointer.get(0);
            VkCommandBuffer result = new VkCommandBuffer(pointer, vkDevice);

            return result;
        }
    }

    /**
     * Allocate command buffers from this device as needed.
     *
     * @param numBuffersNeeded the total number of command buffers needed
     * @param addBuffers storage for allocated command buffers (not null, added
     * to)
     */
    void addCommandBuffers(
            int numBuffersNeeded, List<VkCommandBuffer> addBuffers) {
        numBuffersNeeded -= addBuffers.size();
        if (numBuffersNeeded <= 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create a block of command buffers:
            PointerBuffer pPointers
                    = allocateCommandBuffersInternal(numBuffersNeeded, stack);

            // Add the new buffers to the list:
            for (int i = 0; i < numBuffersNeeded; ++i) {
                long pointer = pPointers.get(i);
                VkCommandBuffer commandBuffer
                        = new VkCommandBuffer(pointer, vkDevice);
                addBuffers.add(commandBuffer);
            }
        }
    }

    /**
     * Create a 2-D image with memory bound to it.
     *
     * @param width the desired width (in pixels)
     * @param height the desired height (in pixels)
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     * @param numSamples the desired number of samples per pixel (a power of 2,
     * &ge;1, &le;64)
     * @param format the desired format
     * @param tiling the desired tiling
     * @param usage a bitmask
     * @param requiredProperties a bitmask
     * @return a new image (not null)
     */
    DeviceImage createImage(int width, int height, int numMipLevels,
            int numSamples, int format, int tiling, int usage,
            int requiredProperties) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);
        Validate.inRange(numSamples, "number of samples per pixel", 1, 64);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
            imageInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);

            imageInfo.arrayLayers(1);
            imageInfo.extent().depth(1);
            imageInfo.extent().height(height);
            imageInfo.extent().width(width);
            imageInfo.format(format);
            imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D);
            imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.mipLevels(numMipLevels);
            imageInfo.samples(numSamples);
            imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.tiling(tiling);
            imageInfo.usage(usage);

            LongBuffer pImage = stack.mallocLong(1);
            int retCode = VK10.vkCreateImage(
                    vkDevice, imageInfo, allocator, pImage);
            Utils.checkForError(retCode, "create an image");
            long imageHandle = pImage.get(0);
            DeviceImage result = new DeviceImage(width, height, imageHandle);

            // Query the images's memory requirements:
            VkMemoryRequirements memRequirements
                    = VkMemoryRequirements.malloc(stack);
            VK10.vkGetImageMemoryRequirements(
                    vkDevice, imageHandle, memRequirements);

            // Allocate memory for the image:
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);

            long allocationBytes = memRequirements.size();
            allocInfo.allocationSize(allocationBytes);

            PhysicalDevice physicalDevice = BaseApplication.getPhysicalDevice();
            int memoryTypeIndex = physicalDevice.findMemoryType(
                    memRequirements.memoryTypeBits(), requiredProperties);
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            retCode = VK10.vkAllocateMemory(
                    vkDevice, allocInfo, allocator, pMemory);
            Utils.checkForError(retCode, "allocate memory for an image");
            long memoryHandle = pMemory.get(0);
            result.associateWithMemory(memoryHandle);

            // Bind the newly allocated memory to the image object:
            int offset = 0;
            retCode = VK10.vkBindImageMemory(
                    vkDevice, imageHandle, memoryHandle, offset);
            Utils.checkForError(retCode, "bind memory to an image");

            return result;
        }
    }

    /**
     * Create a mappable buffer with memory bound to it.
     *
     * @param numBytes the desired buffer capacity (in bytes)
     * @param usage a bitmask
     * @param requiredProperties a bitmask modified)
     * @return the new mappable buffer (not null)
     */
    MappableBuffer createMappable(
            int numBytes, int usage, int requiredProperties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);

            createInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            createInfo.size(numBytes);
            createInfo.usage(usage);

            LongBuffer pBuffer = stack.mallocLong(1);
            int retCode = VK10.vkCreateBuffer(
                    vkDevice, createInfo, allocator, pBuffer);
            Utils.checkForError(retCode, "create a buffer");
            long bufferHandle = pBuffer.get(0);
            MappableBuffer result = new MappableBuffer(numBytes, bufferHandle);

            // Query the buffer's memory requirements:
            VkMemoryRequirements memRequirements
                    = VkMemoryRequirements.malloc(stack);
            VK10.vkGetBufferMemoryRequirements(
                    vkDevice, bufferHandle, memRequirements);

            // Allocate memory for the buffer:
            // TODO a custom allocator to reduce the number of allocations
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);

            long allocationBytes = memRequirements.size();
            allocInfo.allocationSize(allocationBytes);

            PhysicalDevice physicalDevice = BaseApplication.getPhysicalDevice();
            int typeFilter = memRequirements.memoryTypeBits();
            int memoryTypeIndex = physicalDevice.findMemoryType(
                    typeFilter, requiredProperties);
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            LongBuffer pMemory = stack.mallocLong(1);
            retCode = VK10.vkAllocateMemory(
                    vkDevice, allocInfo, allocator, pMemory);
            Utils.checkForError(retCode, "allocate memory for a buffer");
            long memoryHandle = pMemory.get(0);
            result.associateWithMemory(memoryHandle);

            // Bind the newly allocated memory to the buffer:
            int offset = 0;
            retCode = VK10.vkBindBufferMemory(
                    vkDevice, bufferHandle, memoryHandle, offset);
            Utils.checkForError(retCode, "bind memory to a buffer");

            return result;
        }
    }

    /**
     * Update all resources dependent on this device and then destroy the
     * underlying VkDevice.
     *
     * @return null
     */
    LogicalDevice destroy() {
        for (DeviceResource resource : resourceSet) {
            resource.updateLogicalDevice(null);
        }

        if (vkDevice != null) {
            VK10.vkDestroyDevice(vkDevice, allocator);
            this.vkDevice = null;
        }

        return null;
    }

    /**
     * Destroy the specified device buffer, if any.
     *
     * @param handle the handle of the VkBuffer to destroy, or VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long destroyBuffer(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyBuffer(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified image, if any.
     *
     * @param handle the handle of the VkImage to destroy, or VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long destroyImage(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified image view, if any.
     *
     * @param handle the handle of the VkImageView to destroy, or VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long destroyImageView(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified semaphore, if any.
     *
     * @param handle the handle of the VkSemaphor to destroy, or VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long destroySemaphore(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroySemaphore(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified shader module, if any.
     *
     * @param handle the handle of the VkShaderModule to destroy, or
     * VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long destroyShaderModule(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyShaderModule(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Free the specified device memory, if any. If the memory is mapped, it is
     * implicitly unmapped.
     *
     * @param handle the handle of the VkDeviceMemory to free, or VK_NULL_HANDLE
     * @return VK_NULL_HANDLE
     */
    long freeMemory(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Access the underlying VkDevice.
     *
     * @return the pre-existing instance (not null)
     */
    VkDevice getVkDevice() {
        assert vkDevice != null;
        return vkDevice;
    }

    /**
     * Access the first queue in the specified family.
     *
     * @param familyIndex the index of the queue family to access
     * @return a new instance (not null)
     */
    VkQueue getQueue(int familyIndex) {
        int queueIndex = 0; // index within the queue family
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pPointer = stack.mallocPointer(1);
            VK10.vkGetDeviceQueue(vkDevice, familyIndex, queueIndex, pPointer);
            long queueHandle = pPointer.get(0);
            VkQueue result = new VkQueue(queueHandle, vkDevice);

            return result;
        }
    }

    /**
     * Map the specified buffer.
     *
     * @param mappable the buffer to map (not null)
     * @return a new instance (not null)
     */
    ByteBuffer map(MappableBuffer mappable) {
        long memoryHandle = mappable.memoryHandle();
        assert memoryHandle != VK10.VK_NULL_HANDLE;
        int numBytes = mappable.numBytes();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pPointer = stack.mallocPointer(1);
            int offset = 0;
            int flags = 0x0;
            int retCode = VK10.vkMapMemory(
                    vkDevice, memoryHandle, offset, numBytes, flags, pPointer);
            Utils.checkForError(retCode, "map a buffer's memory");

            int index = 0; // the index within pPointer
            ByteBuffer result = pPointer.getByteBuffer(index, numBytes);
            return result;
        }
    }

    /**
     * Unmap the specified buffer.
     *
     * @param mappable the buffer to unmap (not null)
     */
    void unmap(MappableBuffer mappable) {
        long memoryHandle = mappable.memoryHandle();
        if (memoryHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkUnmapMemory(vkDevice, memoryHandle);
        }
    }

    /**
     * Wait for all operations on the device to complete.
     */
    void waitIdle() {
        if (vkDevice != null) {
            int retCode = VK10.vkDeviceWaitIdle(vkDevice);
            // TODO log errors
        }
    }
    // *************************************************************************
    // private methods

    /**
     * Allocate a block of command buffers.
     *
     * @param numBuffersNeeded the number of buffers to allocate (&gt;0)
     * @param stack for memory allocation (not null)
     * @return a buffer containing pointers to new command buffers (not null)
     */
    private PointerBuffer allocateCommandBuffersInternal(
            int numBuffersNeeded, MemoryStack stack) {
        assert numBuffersNeeded > 0 : numBuffersNeeded;

        VkCommandBufferAllocateInfo allocInfo
                = VkCommandBufferAllocateInfo.calloc(stack);
        allocInfo.sType(
                VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);

        allocInfo.commandBufferCount(numBuffersNeeded);
        allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);

        long commandPoolHandle = BaseApplication.commandPoolHandle();
        allocInfo.commandPool(commandPoolHandle);

        PointerBuffer result = stack.mallocPointer(numBuffersNeeded);
        int retCode = VK10.vkAllocateCommandBuffers(
                vkDevice, allocInfo, result);
        Utils.checkForError(retCode, "allocate command buffers");

        return result;
    }
}
