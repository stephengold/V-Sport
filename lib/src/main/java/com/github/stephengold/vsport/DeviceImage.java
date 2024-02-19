/*
 Copyright (c) 2023, Stephen Gold

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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;

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
     * {@code VkImage} handle
     */
    private long imageHandle;
    /**
     * handle of the associated {@code VkDeviceMemory}, or null if none
     */
    private long memoryHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a 2-D image without any associated memory.
     *
     * @param width the width of the image (in pixels, &gt;0)
     * @param height the height of the image (in pixels, &gt;0)
     * @param imageHandle the handle of the {@code VkImage} to use (not null)
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
     * Convert the specified image from one layout to another.
     *
     * @param format the image format
     * @param oldLayout the pre-existing layout
     * @param newLayout the desired layout
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     */
    void alterImageLayout(
            int format, int oldLayout, int newLayout, int numMipLevels) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer pBarrier
                    = VkImageMemoryBarrier.calloc(1, stack);
            pBarrier.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);

            pBarrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            pBarrier.newLayout(newLayout);
            pBarrier.oldLayout(oldLayout);
            pBarrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);

            pBarrier.image(imageHandle);

            int aspectMask;
            if (newLayout
                    == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                aspectMask = VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
                if (Utils.hasStencilComponent(format)) {
                    aspectMask |= VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
                }
            } else {
                aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
            }

            VkImageSubresourceRange range = pBarrier.subresourceRange();
            range.aspectMask(aspectMask);
            range.baseArrayLayer(0);
            range.baseMipLevel(0);
            range.layerCount(1);
            range.levelCount(numMipLevels);

            int sourceStage;
            int destinationStage;

            if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                // UNDEFINED to TRANSFER_DST
                pBarrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                // TRANSFER_DST to SHADER_READ_ONLY
                pBarrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                // UNDEFINED to COLOR_ATTACHMENT
                pBarrier.dstAccessMask(
                        VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage
                        = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                // UNDEFINED to DEPTH_STENCIL_ATTACHMENT
                pBarrier.dstAccessMask(
                        VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage
                        = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;

            } else {
                throw new IllegalArgumentException(
                        "Unsupported transition from layout=" + oldLayout
                        + " to layout=" + newLayout);
            }

            SingleUse commandSequence = new SingleUse();
            commandSequence.addBarrier(sourceStage, destinationStage, pBarrier);
            commandSequence.submitToGraphicsQueue();
        }
    }

    /**
     * Associate the specified memory with this image.
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
     * Destroy the image and free its memory.
     *
     * @return null
     */
    DeviceImage destroy() {
        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        this.imageHandle = logicalDevice.destroyImage(imageHandle);
        this.memoryHandle = logicalDevice.freeMemory(memoryHandle);

        return null;
    }

    /**
     * Return the height of the image.
     *
     * @return the height (in pixels, &gt;0)
     */
    int height() {
        assert height > 0 : height;
        return height;
    }

    /**
     * Return the handle of the image.
     *
     * @return the handle of the pre-existing {@code VkImage} (not null)
     */
    long imageHandle() {
        assert imageHandle != VK10.VK_NULL_HANDLE;
        return imageHandle;
    }

    /**
     * Return the width of the image.
     *
     * @return the width (in pixels, &gt;0)
     */
    int width() {
        assert width > 0 : width;
        return width;
    }
}
