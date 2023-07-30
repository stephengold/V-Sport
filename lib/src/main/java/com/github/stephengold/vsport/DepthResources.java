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

import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;

/**
 * Encapsulate depth-buffer resources in the V-Sport graphics engine.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class DepthResources {
    // *************************************************************************
    // fields

    /**
     * handle of the VkImage
     */
    private long imageHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the VkDeviceMemory
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the VkImageView
     */
    private long viewHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate depth-buffer resources.
     *
     * @param format the desired image format
     * @param extent the desired extent (in pixels, not null, unaffected)
     */
    DepthResources(int format, VkExtent2D extent) {
        int tiling = VK10.VK_IMAGE_TILING_OPTIMAL;
        int width = extent.width();
        int height = extent.height();
        int numMipLevels = 1;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pImageHandle = stack.mallocLong(1);
            LongBuffer pMemoryHandle = stack.mallocLong(1);
            BaseApplication.createImage(width, height, numMipLevels, format,
                    tiling, VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT,
                    VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                    pImageHandle, pMemoryHandle);
            this.imageHandle = pImageHandle.get(0);
            this.memoryHandle = pMemoryHandle.get(0);

            this.viewHandle = BaseApplication.createImageView(imageHandle,
                    format, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, numMipLevels);

            // Immediately transition the image to optimal layout:
            BaseApplication.alterImageLayout(
                    imageHandle, format, VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                    VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                    numMipLevels);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy all owned resources.
     */
    void destroy() {
        VkDevice logicalDevice = BaseApplication.getLogicalDevice();
        VkAllocationCallbacks allocator = BaseApplication.allocator();
        /*
         * Destroy resources in the reverse of the order they were created,
         * starting with the VkImageView.
         */
        if (viewHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImageView(logicalDevice, viewHandle, allocator);
            viewHandle = VK10.VK_NULL_HANDLE;
        }
        if (memoryHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkFreeMemory(logicalDevice, memoryHandle, allocator);
            memoryHandle = VK10.VK_NULL_HANDLE;
        }
        if (imageHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyImage(logicalDevice, imageHandle, allocator);
            imageHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Return the handle of the VkImageView.
     *
     * @return the handle (not null)
     */
    long viewHandle() {
        return viewHandle;
    }
}
