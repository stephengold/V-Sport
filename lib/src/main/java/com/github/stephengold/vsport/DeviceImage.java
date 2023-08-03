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
 * A VkImage and its associated device memory, both allocated from a specific
 * LogicalDevice.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class DeviceImage {
    // *************************************************************************
    // fields

    /**
     * height of the image (in pixels, &gt;0)
     */
    final private int height;
    /**
     * width of the image (in pixels, &gt;0)
     */
    final private int width;
    /**
     * handle of the VkImage
     */
    private long imageHandle;
    /**
     * handle of the associated VkDeviceMemory, or VK_NULL_HANDLE if none
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a 2-D image without any associated memory.
     *
     * @param width the width of the image (in pixels, &gt;0)
     * @param height the height of the image (in pixels, &gt;0)
     * @param imageHandle the handle of the VkImage (not VK_NULL_HANDLE)
     */
    DeviceImage(int width, int height, long imageHandle) {
        Validate.positive(width, "width");
        Validate.positive(height, "height");
        Validate.nonZero(imageHandle, "image handle");

        this.width = width;
        this.height = height;
        this.imageHandle = imageHandle;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Associate the specified memory with this image.
     *
     * @param handle a VkDeviceMemory handle (not VK_NULL_HANDLE)
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
     * Destroy the image and free its memory.
     *
     * @return null
     */
    DeviceImage destroy() {
        LogicalDevice logicalDevice = BaseApplication.getLogicalDevice();
        this.imageHandle = logicalDevice.destroyImage(imageHandle);
        this.memoryHandle = logicalDevice.freeMemory(memoryHandle);

        return null;
    }

    /**
     * Return the height of the image, in pixels.
     *
     * @return the height (&gt;0)
     */
    int height() {
        assert height > 0 : height;
        return height;
    }

    /**
     * Return the handle of the image.
     *
     * @return a VkImage handle (not VK_NULL_HANDLE)
     */
    long imageHandle() {
        assert imageHandle != VK10.VK_NULL_HANDLE;
        return imageHandle;
    }

    /**
     * Return the handle of the associated memory, if any.
     *
     * @return a VkDeviceMemory handle, or VK_NULL_HANDLE if none set
     */
    long memoryHandle() {
        return memoryHandle;
    }

    /**
     * Return the width of the image, in pixels.
     *
     * @return the width (&gt;0)
     */
    int width() {
        assert width > 0 : width;
        return width;
    }
}
