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

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;

/**
 * Encapsulate the handles of a Vulkan texture.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Texture {
    // *************************************************************************
    // fields

    /**
     * height of the original image (in pixels)
     */
    final private int height;
    /**
     * width of the original image (in pixels)
     */
    final private int width;
    /**
     * handle of the image resource (native type: VkImage)
     */
    private long imageHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the image's memory (native type: VkDeviceMemory)
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the image view (native type: VkImageView)
     */
    private long viewHandle = VK10.VK_NULL_HANDLE;
    /**
     * allocator for direct buffers, or null to use the default allocator
     */
    final private VkAllocationCallbacks allocator;
    /**
     * logical device that owns this instance
     */
    final private VkDevice logicalDevice;
    // *************************************************************************
    // constructors

    /**
     * Instantiate and load a texture from the named class-path resource.
     *
     * @param resourceName the name of the resource (not null)
     */
    Texture(String resourceName) {
        this.logicalDevice = BaseApplication.logicalDevice();
        this.allocator = BaseApplication.allocator();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            BufferedImage image = Utils.loadResourceAsImage(resourceName);
            /*
             * Note: loading with AWT instead of STB
             * (which doesn't handle InputStream input).
             */
            int numChannels = 4;
            this.width = image.getWidth();
            this.height = image.getHeight();
            int numBytes = width * height * numChannels;

            // Create a temporary buffer object for staging:
            int createUsage = VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
            int properties = VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                    | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            LongBuffer pBufferHandle = stack.mallocLong(1);
            LongBuffer pMemoryHandle = stack.mallocLong(1);
            BaseApplication.createBuffer(numBytes, createUsage, properties,
                    pBufferHandle, pMemoryHandle);
            long stagingBufferHandle = pBufferHandle.get(0);
            long stagingMemoryHandle = pMemoryHandle.get(0);

            // Temporarily map the staging buffer's memory:
            int offset = 0;
            int flags = 0x0;
            PointerBuffer pPointer = stack.mallocPointer(1);
            int retCode = VK10.vkMapMemory(logicalDevice,
                    stagingMemoryHandle, offset, numBytes, flags, pPointer);
            Utils.checkForError(retCode, "map staging buffer's memory");

            int index = 0; // the index within pPointer
            ByteBuffer data = pPointer.getByteBuffer(index, numBytes);
            /*
             * Copy pixel-by-pixel to the staging buffer and then unmap the
             * staging buffer.
             */
            for (int x = 0; x < width; ++x) {
                for (int y = 0; y < height; ++y) {
                    int argb = image.getRGB(x, y);
                    int red = (argb >> 16) & 0xFF;
                    int green = (argb >> 8) & 0xFF;
                    int blue = argb & 0xFF;
                    int alpha = (argb >> 24) & 0xFF;
                    data.put((byte) red)
                            .put((byte) green)
                            .put((byte) blue)
                            .put((byte) alpha);
                }
            }
            VK10.vkUnmapMemory(logicalDevice, stagingMemoryHandle);
            /*
             * Create a device-local image that's optimized for being
             * a copy destination:
             */
            int textureFormat = VK10.VK_FORMAT_R8G8B8A8_SRGB;
            createUsage = VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT
                    | VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
            properties = VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            LongBuffer pImageHandle = stack.mallocLong(1);
            BaseApplication.createImage(width, height, textureFormat,
                    VK10.VK_IMAGE_TILING_OPTIMAL, createUsage, properties,
                    pImageHandle, pMemoryHandle);
            this.imageHandle = pImageHandle.get(0);
            this.memoryHandle = pMemoryHandle.get(0);
            BaseApplication.alterImageLayout(imageHandle, textureFormat,
                    VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);

            // Copy the data from the staging buffer the new image:
            BaseApplication.copyBufferToImage(
                    stagingBufferHandle, imageHandle, width, height);

            // Convert the image to a layout optimized for sampling:
            BaseApplication.alterImageLayout(imageHandle, textureFormat,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            // Destroy the staging buffer and free its memory:
            VK10.vkDestroyBuffer(
                    logicalDevice, stagingBufferHandle, allocator);
            VK10.vkFreeMemory(logicalDevice, stagingMemoryHandle, allocator);

            // Create a view for the new image:
            this.viewHandle = BaseApplication.createImageView(
                    imageHandle, textureFormat,
                    VK10.VK_IMAGE_ASPECT_COLOR_BIT);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy the LWJGL resources and free the associated memory.
     */
    void destroy() {
        if (viewHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(
                    logicalDevice, viewHandle, allocator);
            this.viewHandle = VK10.VK_NULL_HANDLE;
        }
        if (memoryHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(
                    logicalDevice, memoryHandle, allocator);
            this.memoryHandle = VK10.VK_NULL_HANDLE;
        }
        if (imageHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(
                    logicalDevice, imageHandle, allocator);
            this.imageHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Return the handle of the image view.
     *
     * @return the handle
     */
    final long viewHandle() {
        return viewHandle;
    }
}
