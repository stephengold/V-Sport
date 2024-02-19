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

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import jme3utilities.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

/**
 * Resources used to generate the commands for a render pass.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Pass {
    // *************************************************************************
    // fields

    /**
     * uniform buffer object (UBO) for scene-level uniforms
     */
    private BufferResource globalUbo;
    /**
     * draw-command resources (one for each geometry to be drawn)
     */
    final private List<Draw> drawList = new ArrayList<>(4);
    /**
     * geometries to draw
     */
    final private List<Geometry> geometryList = new ArrayList<>(4);
    /**
     * {@code VkDescriptorSetLayout} handle
     */
    final private long descriptorSetLayoutHandle;
    /**
     * {@code VkFrameBuffer} handle
     */
    private long framebufferHandle;
    /**
     * handle of the {@code VkDescriptorPool} for allocation
     */
    final private long poolHandle;
    /**
     * handle of the {@code VkImageView} for presentation
     */
    private long presentViewHandle;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a render pass.
     *
     * @param imageHandle the handle of the {@code VkImage} for presentation
     * (not null)
     * @param dsPoolHandle handle of the descriptor-set pool for allocation (not
     * null)
     * @param dsLayoutHandle the handle of the descriptor-set layout (not null)
     * @param imageFormat the image format for presentation
     * @param colorAttachment the color attachment for the framebuffer (may be
     * null)
     * @param depthAttachment the depth attachment for the framebuffer (not
     * null)
     * @param passHandle the render-pass handle (not null)
     * @param framebufferExtent the dimensions for the frame buffer (not null)
     */
    Pass(long imageHandle, long dsPoolHandle, long dsLayoutHandle,
            int imageFormat, Attachment colorAttachment,
            Attachment depthAttachment, long passHandle,
            VkExtent2D framebufferExtent) {
        Validate.nonZero(imageHandle, "image handle");
        Validate.nonZero(dsPoolHandle, "descriptor-set pool handle");
        Validate.nonZero(dsLayoutHandle, "descriptor-set layout handle");
        Validate.nonNull(depthAttachment, "depth attachment");
        Validate.nonZero(passHandle, "render-pass handle");
        Validate.nonNull(framebufferExtent, "framebuffer extent");

        this.poolHandle = dsPoolHandle;
        this.descriptorSetLayoutHandle = dsLayoutHandle;

        int usage = VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
        int numBytes = GlobalUniformValues.numBytes();
        boolean staging = false;
        this.globalUbo = new BufferResource(numBytes, usage, staging);

        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        int numMipLevels = 1;
        this.presentViewHandle = logicalDevice.createImageView(imageHandle,
                imageFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT, numMipLevels);

        this.framebufferHandle = createFramebuffer(
                passHandle, framebufferExtent, colorAttachment, depthAttachment,
                presentViewHandle);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy all resources owned by this instance.
     */
    void destroy() {
        geometryList.clear();

        for (Draw draw : drawList) {
            draw.destroy();
        }
        drawList.clear();

        VkAllocationCallbacks allocator = Internals.findAllocator();
        VkDevice vkDevice = Internals.getVkDevice();
        if (framebufferHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFramebuffer(vkDevice, framebufferHandle, allocator);
            this.framebufferHandle = VK10.VK_NULL_HANDLE;
        }

        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        this.presentViewHandle
                = logicalDevice.destroyImageView(presentViewHandle);

        if (globalUbo != null) {
            globalUbo.destroy();
            this.globalUbo = null;
        }
    }

    /**
     * Return the framebuffer handle for the specified presentation image.
     *
     * @return the handle of the pre-existing VkFramebuffer (not null)
     */
    long framebufferHandle() {
        assert framebufferHandle != VK10.VK_NULL_HANDLE;
        return framebufferHandle;
    }

    /**
     * Access the draw resources for the specified geometry, allocating a new
     * one if necessary.
     *
     * @param geometryIndex the index of the corresponding geometry (&ge;0)
     * @return a new or pre-existing instance (not null)
     */
    Draw getDraw(int geometryIndex) {
        while (geometryIndex >= drawList.size()) {
            Draw draw
                    = new Draw(poolHandle, descriptorSetLayoutHandle);
            drawList.add(draw);
        }
        Draw result = drawList.get(geometryIndex);

        assert result != null;
        return result;
    }

    /**
     * Access the geometry list.
     *
     * @return the pre-existing instance (not null)
     */
    List<Geometry> getGeometryList() {
        return geometryList;
    }

    /**
     * Access the global Uniform Buffer Object (UBO).
     *
     * @return the pre-existing instance (not null)
     */
    BufferResource getGlobalUbo() {
        assert globalUbo != null;
        return globalUbo;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Create a framebuffer.
     *
     * @param passHandle the render-pass handle (not null)
     * @param framebufferExtent the dimensions for the frame buffer (not null)
     * @param colorAttachment the color attachment for the framebuffer (may be
     * null)
     * @param depthAttachment the depth attachment for the framebuffer (not
     * null)
     * @param presentViewHandle handle of the image view for presentation
     * @return the {@code VkFramebuffer} handle of the new framebuffer (not
     * null)
     */
    private static long createFramebuffer(long passHandle,
            VkExtent2D framebufferExtent, Attachment colorAttachment,
            Attachment depthAttachment, long presentViewHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // create a framebuffer:
            VkFramebufferCreateInfo createInfo
                    = VkFramebufferCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);

            createInfo.layers(1);
            createInfo.renderPass(passHandle);

            int height = framebufferExtent.height();
            createInfo.height(height);

            int width = framebufferExtent.width();
            createInfo.width(width);

            // order of attachments must match that in createPass()
            long depthViewHandle = depthAttachment.viewHandle();
            LongBuffer pAttachmentHandles;
            if (colorAttachment == null) {
                pAttachmentHandles
                        = stack.longs(presentViewHandle, depthViewHandle);
            } else {
                long colorViewHandle = colorAttachment.viewHandle();
                pAttachmentHandles = stack.longs(
                        colorViewHandle, depthViewHandle, presentViewHandle);
            }
            createInfo.pAttachments(pAttachmentHandles);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateFramebuffer(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create a framebuffer");
            long result = pHandle.get(0);

            return result;
        }
    }
}
