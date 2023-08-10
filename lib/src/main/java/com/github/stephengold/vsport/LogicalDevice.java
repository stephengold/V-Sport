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
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import jme3utilities.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

/**
 * Encapsulate a logical device, used to allocate resources such as command
 * buffers, images, mappable buffers, pipelines, semaphores, and shader modules.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
public class LogicalDevice {
    // *************************************************************************
    // fields

    /**
     * {@code VkCommandPool} handle for allocating command buffers
     */
    private long commandPoolHandle = VK10.VK_NULL_HANDLE;
    /**
     * all device-dependent resources
     */
    final private static Collection<DeviceResource> resourceSet
            = new TreeSet<>();
    /**
     * allocator for direct buffers
     */
    private final VkAllocationCallbacks allocator;
    /**
     * underlying lwjgl-vulkan device
     */
    private VkDevice vkDevice;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a logical device, create a command-buffer pool, and update
     * all device resources.
     *
     * @param physicalDevice the underlying physical device (not null)
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} for
     * presentation (not null)
     */
    LogicalDevice(PhysicalDevice physicalDevice, long surfaceHandle) {
        Validate.nonZero(surfaceHandle, "surface handle");

        boolean enableDebugging = BaseApplication.isDebuggingEnabled();
        this.vkDevice = physicalDevice
                .createLogicalDevice(surfaceHandle, enableDebugging);

        this.allocator = Internals.findAllocator();
        this.commandPoolHandle
                = createCommandPool(physicalDevice, surfaceHandle);

        for (DeviceResource resource : resourceSet) {
            resource.updateLogicalDevice(this);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Allocate command buffers from the device as needed.
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
     * Allocate a single command buffer from the device.
     *
     * @return the new {@code VkCommandBuffer} (not null)
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

            PhysicalDevice physicalDevice = Internals.getPhysicalDevice();
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
     * Create a view for the specified image.
     *
     * @param imageHandle the handle of the image (not null)
     * @param format the desired format for the view
     * @param aspectMask a bitmask of VK_IMAGE_ASPECT_... values
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     * @return the handle of the new {@code VkImageView} (not null)
     */
    long createImageView(
            long imageHandle, int format, int aspectMask, int numMipLevels) {
        Validate.nonZero(imageHandle, "image handle");
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo
                    = VkImageViewCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);

            createInfo.format(format);
            createInfo.image(imageHandle);
            createInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);

            // Don't swizzle the color channels:
            VkComponentMapping swizzle = createInfo.components();
            swizzle.r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);

            // a single-layer view
            VkImageSubresourceRange range = createInfo.subresourceRange();
            range.aspectMask(aspectMask);
            range.baseArrayLayer(0);
            range.baseMipLevel(0);
            range.layerCount(1);
            range.levelCount(numMipLevels);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateImageView(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create image view");
            long result = pHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
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

            PhysicalDevice physicalDevice = Internals.getPhysicalDevice();
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
     * Create an image-available queue semaphore in the un-signaled state.
     *
     * @return the handle of the new {@code VkSemaphore} (not null)
     */
    long createSemaphore() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo createInfo
                    = VkSemaphoreCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateSemaphore(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create a semaphore");
            long result = pHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }

    /**
     * Destroy all resources that depend on this device and then destroy the
     * underlying {@code VkDevice}.
     *
     * @return null
     */
    LogicalDevice destroy() {
        for (DeviceResource resource : resourceSet) {
            resource.updateLogicalDevice(null);
        }

        // Destroy the command pool and its buffers:
        if (commandPoolHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyCommandPool(vkDevice, commandPoolHandle, allocator);
            this.commandPoolHandle = VK10.VK_NULL_HANDLE;
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
     * @param handle the handle of the {@code VkBuffer} to destroy, or null
     * @return null
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
     * @param handle the handle of the {@code VkImage} to destroy, or null
     * @return null
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
     * @param handle the handle of the {@code VkImageView} to destroy, or null
     * @return null
     */
    long destroyImageView(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified pipeline, if any.
     *
     * @param handle the handle of the {@code VkPipeline} to destroy, or null
     * @return null
     */
    long destroyPipeline(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Destroy the specified semaphore, if any.
     *
     * @param handle the handle of the {@code VkSemaphore} to destroy, or null
     * @return null
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
     * @param handle the handle of the {@code VkShaderModule} to destroy, or
     * null
     * @return null
     */
    long destroyShaderModule(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyShaderModule(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
    }

    /**
     * Free the specified command buffer, if any.
     *
     * @param commandBuffer the command buffer to free (may be null)
     * @return null
     */
    VkCommandBuffer freeCommandBuffer(VkCommandBuffer commandBuffer) {
        if (commandBuffer != null) {
            VK10.vkFreeCommandBuffers(
                    vkDevice, commandPoolHandle, commandBuffer);
        }
        return null;
    }

    /**
     * Free the specified device memory, if any. If the memory is mapped, it is
     * implicitly unmapped.
     *
     * @param handle the handle of the {@code VkDeviceMemory} to free, or null
     * @return null
     */
    long freeMemory(long handle) {
        if (handle != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(vkDevice, handle, allocator);
        }

        return VK10.VK_NULL_HANDLE;
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
     * Access the underlying VkDevice.
     *
     * @return the pre-existing instance (not null)
     */
    VkDevice getVkDevice() {
        assert vkDevice != null;
        return vkDevice;
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
     * Stop tracking the specified resource.
     *
     * @param resource the resource to stop tracking (not null)
     */
    static void stopTrackingResource(DeviceResource resource) {
        Validate.nonNull(resource, "resource");
        assert resourceSet.contains(resource);

        resourceSet.remove(resource);
    }

    /**
     * Begin tracking the specified resource.
     *
     * @param resource the resource to track (not null)
     */
    static void trackResource(DeviceResource resource) {
        Validate.nonNull(resource, "resource");
        assert !resourceSet.contains(resource);

        resourceSet.add(resource);
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
     * Wait for all operations on the GPU device to complete.
     */
    void waitIdle() {
        if (vkDevice != null) {
            int retCode = VK10.vkDeviceWaitIdle(vkDevice);
            if (retCode != VK10.VK_SUCCESS) {
                System.err.println(
                        "vkDeviceWaitIdle returned unexpected value: "
                        + retCode);
                System.err.flush();
            }
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
        allocInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);

        allocInfo.commandBufferCount(numBuffersNeeded);
        allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);

        allocInfo.commandPool(commandPoolHandle);

        PointerBuffer result = stack.mallocPointer(numBuffersNeeded);
        int retCode = VK10.vkAllocateCommandBuffers(
                vkDevice, allocInfo, result);
        Utils.checkForError(retCode, "allocate command buffers");

        return result;
    }

    /**
     * Create an (empty) command-buffer pool for graphics queues.
     *
     * @param physicalDevice the underlying physical device (not null)
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} for
     * presentation (not null)
     *
     * @return the handle of a new, empty {@code VkCommandPool}
     */
    private long createCommandPool(
            PhysicalDevice physicalDevice, long surfaceHandle) {
        Validate.nonZero(surfaceHandle, "surface handle");

        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);
        int familyIndex = queueFamilies.graphics();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo
                    = VkCommandPoolCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);

            createInfo.flags(
                    VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            createInfo.queueFamilyIndex(familyIndex);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateCommandPool(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create a command-buffer pool");
            long result = pHandle.get(0);

            return result;
        }
    }
}
