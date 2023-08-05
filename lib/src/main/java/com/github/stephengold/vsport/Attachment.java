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

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkExtent2D;

/**
 * A framebuffer attachment in the V-Sport graphics engine.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class Attachment {
    // *************************************************************************
    // fields

    /**
     * underlying image
     */
    private DeviceImage deviceImage;
    /**
     * final image layout
     */
    final int finalLayout;
    /**
     * image format code
     */
    final int format;
    /**
     * number of samples per pixel (&gt;0)
     */
    final int numSamples;
    /**
     * handle of the VkImageView
     */
    private long viewHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate an attachment for a depth buffer or transient color buffer.
     *
     * @param format the desired image format
     * @param extent the desired extent (in pixels, not null, unaffected)
     * @param aspectMask the desired aspect (a VK10.VK_IMAGE_ASPECT_... bit)
     * @param numSamples the desired number of samples per pixel (&gt;0)
     */
    Attachment(int format, VkExtent2D extent, int aspectMask, int numSamples) {
        this.format = format;
        this.numSamples = numSamples;

        int width = extent.width();
        int height = extent.height();
        int numMipLevels = 1;
        int tiling = VK10.VK_IMAGE_TILING_OPTIMAL;

        int usage;
        if (aspectMask == VK10.VK_IMAGE_ASPECT_COLOR_BIT) {
            // transient color buffer
            this.finalLayout = VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            usage = VK10.VK_IMAGE_USAGE_TRANSIENT_ATTACHMENT_BIT
                    | VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;

        } else if (aspectMask == VK10.VK_IMAGE_ASPECT_DEPTH_BIT) {
            // depth buffer
            this.finalLayout
                    = VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
            usage = VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;

        } else {
            throw new IllegalArgumentException("aspect = " + aspectMask);
        }

        LogicalDevice logicalDevice = BaseApplication.getLogicalDevice();
        this.deviceImage = logicalDevice.createImage(
                width, height, numMipLevels, numSamples, format, tiling,
                usage, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long imageHandle = deviceImage.imageHandle();
        this.viewHandle = logicalDevice.createImageView(
                imageHandle, format, aspectMask, numMipLevels);

        // Immediately transition the image to an optimal layout:
        BaseApplication.alterImageLayout(deviceImage, format,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED, finalLayout, numMipLevels);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Describe the attachment when creating a render pass.
     *
     * @param description (not null, modified)
     */
    void describe(VkAttachmentDescription description) {
        description.finalLayout(finalLayout);
        description.format(format);
        description.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
        description.samples(numSamples);

        // no stencil operations:
        description.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
        description.stencilStoreOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);

        // Clear the buffer before each frame:
        description.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
        description.storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);
    }

    /**
     * Destroy all resources owned by the attachment.
     *
     * @return null
     */
    Attachment destroy() {
        LogicalDevice logicalDevice = BaseApplication.getLogicalDevice();
        this.viewHandle = logicalDevice.destroyImageView(viewHandle);
        if (deviceImage != null) {
            this.deviceImage = deviceImage.destroy();
        }

        return null;
    }

    /**
     * Return the image format.
     *
     * @return the format code
     */
    int imageFormat() {
        return format;
    }

    /**
     * Access the image view.
     *
     * @return the handle of the pre-existing {@code VkImageView} (not null)
     */
    long viewHandle() {
        assert viewHandle != VK10.VK_NULL_HANDLE;
        return viewHandle;
    }
}
