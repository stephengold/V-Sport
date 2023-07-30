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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkMemoryBarrier;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;

/**
 * Encapsulate a Vulkan command buffer, typically for a single use.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
public class Commands {
    // *************************************************************************
    // fields

    /**
     * command buffer allocated from the default pool
     */
    private VkCommandBuffer commandBuffer;
    // *************************************************************************
    // constructors

    /**
     * Begin recording a single-use command sequence.
     */
    Commands() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Allocate a temporary command buffer:
            // TODO a pool of short-lived command buffers - see copyBuffer()
            VkCommandBufferAllocateInfo allocInfo
                    = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);

            allocInfo.commandBufferCount(1);
            long commandPoolHandle = BaseApplication.commandPoolHandle();
            allocInfo.commandPool(commandPoolHandle);
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);

            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            PointerBuffer pPointer = stack.mallocPointer(1);
            int retCode = VK10.vkAllocateCommandBuffers(
                    logicalDevice, allocInfo, pPointer);
            Utils.checkForError(retCode, "allocate a command buffer");
            long pointer = pPointer.get(0);
            this.commandBuffer = new VkCommandBuffer(pointer, logicalDevice);

            VkCommandBufferBeginInfo beginInfo
                    = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            beginInfo.flags(VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);

            retCode = VK10.vkBeginCommandBuffer(commandBuffer, beginInfo);
            Utils.checkForError(retCode, "begin recording commands");
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Append a simple pipeline-barrier command to the sequence.
     *
     * @param sourceStages a bitmask specifying the source pipeline stages
     * @param destStages a bitmask specifying the destination pipeline stages
     * @param pImBarriers the image-memory pipeline barriers to use
     * @return the current sequence (for chaining)
     */
    Commands addBarrier(int sourceStages, int destStages,
            VkImageMemoryBarrier.Buffer pImBarriers) {
        int dependencyFlags = 0x0;
        VkMemoryBarrier.Buffer pMemoryBarriers = null;
        VkBufferMemoryBarrier.Buffer pBmBarriers = null;

        VK10.vkCmdPipelineBarrier(commandBuffer, sourceStages, destStages,
                dependencyFlags, pMemoryBarriers, pBmBarriers, pImBarriers);

        return this;
    }

    /**
     * Append a command to blit data within a single image, using a linear
     * filter.
     *
     * @param imageHandle the handle of source/destination image
     * @param pBlits the regions to be blitted
     * @return the current sequence (for chaining)
     */
    Commands addBlit(long imageHandle, VkImageBlit.Buffer pBlits) {
        VK10.vkCmdBlitImage(commandBuffer,
                imageHandle, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                imageHandle, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                pBlits, VK10.VK_FILTER_LINEAR);

        return this;
    }

    /**
     * Append a simple buffer-to-buffer copy command to the sequence.
     *
     * @param numBytes the number of bytes to copy (&ge;0)
     * @param sourceHandle the handle of the source buffer
     * @param destHandle the handle of the destination buffer
     * @return the current sequence (for chaining)
     */
    Commands addCopyBufferToBuffer(
            long numBytes, long sourceHandle, long destHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer pRegion = VkBufferCopy.calloc(1, stack);
            pRegion.dstOffset(0);
            pRegion.size(numBytes);
            pRegion.srcOffset(0);

            VK10.vkCmdCopyBuffer(
                    commandBuffer, sourceHandle, destHandle, pRegion);
        }

        return this;
    }

    /**
     * Append a simple buffer-to-image copy command to the sequence. The final
     * image layout is TRANSFER_DST.
     *
     * @param bufferHandle the handle of the source buffer
     * @param imageHandle the handle of the destination image
     * @param width the width of the image (in pixels)
     * @param height the height of the image (in pixels)
     * @return the current sequence (for chaining)
     */
    Commands addCopyBufferToImage(
            long bufferHandle, long imageHandle, int width, int height) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer pRegions
                    = VkBufferImageCopy.calloc(1, stack);
            pRegions.bufferImageHeight(0);  // tightly packed
            pRegions.bufferOffset(0);
            pRegions.bufferRowLength(0);    // tightly packed
            pRegions.imageOffset().set(0, 0, 0);

            VkExtent3D extent = VkExtent3D.calloc(stack);
            int depth = 1;
            extent.set(width, height, depth);
            pRegions.imageExtent(extent);

            VkImageSubresourceLayers sub = pRegions.imageSubresource();
            sub.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            sub.baseArrayLayer(0);
            sub.layerCount(1);
            sub.mipLevel(0);

            VK10.vkCmdCopyBufferToImage(
                    commandBuffer, bufferHandle, imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pRegions);
        }

        return this;
    }

    /**
     * Terminate the sequence, submit it to a queue with graphics capabilities,
     * wait for it to complete, and free the command buffer.
     */
    void submitToGraphicsQueue() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int retCode = VK10.vkEndCommandBuffer(commandBuffer);
            Utils.checkForError(retCode, "terminate a command sequence");

            // info to submit a command buffer to the graphics queue:
            VkSubmitInfo.Buffer pSubmitInfo = VkSubmitInfo.calloc(1, stack);
            pSubmitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);

            PointerBuffer pCommandBuffer = stack.pointers(commandBuffer);
            pSubmitInfo.pCommandBuffers(pCommandBuffer);

            // Submit the command buffer to the graphics queue:
            VkQueue queue = BaseApplication.getGraphicsQueue();
            long fenceHandle = VK10.VK_NULL_HANDLE;
            retCode = VK10.vkQueueSubmit(queue, pSubmitInfo, fenceHandle);
            Utils.checkForError(retCode, "submit a command sequence");

            // Wait until the graphics queue is idle:
            // TODO use a fence to submit multiple command sequences in parallel
            retCode = VK10.vkQueueWaitIdle(queue);
            Utils.checkForError(retCode, "wait for a queue to be idle");

            // Free the command buffer:
            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            long commandPoolHandle = BaseApplication.commandPoolHandle();
            VK10.vkFreeCommandBuffers(
                    logicalDevice, commandPoolHandle, commandBuffer);
            this.commandBuffer = null;
        }
    }
}
