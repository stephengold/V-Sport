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
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkBufferMemoryBarrier;
import org.lwjgl.vulkan.VkCommandBuffer;
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
 * A container for commands to be submitted to a queue.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class CommandSequence {
    // *************************************************************************
    // fields

    /**
     * underlying command buffer
     */
    private VkCommandBuffer vkCommandBuffer;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a new sequence, allocating the command buffer from the
     * default pool.
     */
    CommandSequence() {
        LogicalDevice logicalDevice = BaseApplication.getLogicalDevice();
        this.vkCommandBuffer = logicalDevice.allocateCommandBuffer();
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
    CommandSequence addBarrier(int sourceStages, int destStages,
            VkImageMemoryBarrier.Buffer pImBarriers) {
        int dependencyFlags = 0x0;
        VkMemoryBarrier.Buffer pMemoryBarriers = null;
        VkBufferMemoryBarrier.Buffer pBmBarriers = null;
        VK10.vkCmdPipelineBarrier(vkCommandBuffer, sourceStages, destStages,
                dependencyFlags, pMemoryBarriers, pBmBarriers, pImBarriers);

        return this;
    }

    /**
     * Append a command to blit data within a single image, using a linear
     * filter.
     *
     * @param image the source/destination image (not null)
     * @param pBlits the regions to be blitted
     * @return the current sequence (for chaining)
     */
    CommandSequence addBlit(DeviceImage image, VkImageBlit.Buffer pBlits) {
        long imageHandle = image.imageHandle();
        VK10.vkCmdBlitImage(vkCommandBuffer,
                imageHandle, VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                imageHandle, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                pBlits, VK10.VK_FILTER_LINEAR);

        return this;
    }

    /**
     * Append a simple buffer-to-buffer copy command to the sequence.
     *
     * @param source the source buffer (not null)
     * @param destination the destination buffer (not null, same capacity as
     * source)
     * @return the current sequence (for chaining)
     */
    CommandSequence addCopyBufferToBuffer(
            MappableBuffer source, MappableBuffer destination) {
        int numBytes = source.numBytes();
        assert destination.numBytes() == source.numBytes();

        long sourceHandle = source.vkBufferHandle();
        long destHandle = destination.vkBufferHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCopy.Buffer pRegion = VkBufferCopy.calloc(1, stack);
            pRegion.dstOffset(0);
            pRegion.size(numBytes);
            pRegion.srcOffset(0);

            VK10.vkCmdCopyBuffer(
                    vkCommandBuffer, sourceHandle, destHandle, pRegion);
        }

        return this;
    }

    /**
     * Append a simple buffer-to-image copy command to the sequence. The final
     * image layout is TRANSFER_DST.
     *
     * @param source the source buffer (not null)
     * @param destination the destination image (not null)
     * @return the current sequence (for chaining)
     */
    CommandSequence addCopyBufferToImage(
            MappableBuffer source, DeviceImage destination) {
        long bufferHandle = source.vkBufferHandle();
        long imageHandle = destination.imageHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferImageCopy.Buffer pRegions
                    = VkBufferImageCopy.calloc(1, stack);
            pRegions.bufferImageHeight(0);  // tightly packed
            pRegions.bufferOffset(0);
            pRegions.bufferRowLength(0);    // tightly packed
            pRegions.imageOffset().set(0, 0, 0);

            VkExtent3D extent = VkExtent3D.calloc(stack);
            int width = destination.width();
            int height = destination.height();
            int depth = 1;
            extent.set(width, height, depth);
            pRegions.imageExtent(extent);

            VkImageSubresourceLayers sub = pRegions.imageSubresource();
            sub.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT);
            sub.baseArrayLayer(0);
            sub.layerCount(1);
            sub.mipLevel(0);

            VK10.vkCmdCopyBufferToImage(
                    vkCommandBuffer, bufferHandle, imageHandle,
                    VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, pRegions);
        }

        return this;
    }

    /**
     * Free the underlying {@code VkCommandBuffer}.
     *
     * @return null
     */
    CommandSequence destroy() {
        VkDevice vkDevice = BaseApplication.getVkDevice();
        long commandPoolHandle = BaseApplication.commandPoolHandle();
        VK10.vkFreeCommandBuffers(vkDevice, commandPoolHandle, vkCommandBuffer);
        this.vkCommandBuffer = null;

        return null;
    }

    /**
     * Terminate the recorded sequence, but don't submit it yet.
     *
     * @return the modified sequence, for chaining
     */
    CommandSequence end() {
        int retCode = VK10.vkEndCommandBuffer(vkCommandBuffer);
        Utils.checkForError(retCode, "terminate a command sequence");

        return this;
    }

    /**
     * Begin recording a new series of commands.
     *
     * @param flags the flags to pass to {@code vkBeginCommandBuffer()}
     * @return the modified sequence, for chaining
     */
    CommandSequence reset(int flags) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo beginInfo
                    = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            beginInfo.flags(flags);

            int retCode = VK10.vkBeginCommandBuffer(vkCommandBuffer, beginInfo);
            Utils.checkForError(retCode, "begin recording commands");
        }

        return this;
    }

    /**
     * Submit the recorded sequence to the specified queue and wait for it to
     * complete.
     *
     * @param queue the queue to submit to (not null)
     * @return the current sequence, for chaining
     */
    CommandSequence submitTo(VkQueue queue) {
        Validate.nonNull(queue, "queue");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Submit to the queue:
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);

            PointerBuffer pCommandBuffer = stack.pointers(vkCommandBuffer);
            submitInfo.pCommandBuffers(pCommandBuffer);

            long fenceHandle = VK10.VK_NULL_HANDLE;
            int retCode = VK10.vkQueueSubmit(queue, submitInfo, fenceHandle);
            Utils.checkForError(
                    retCode, "submit a command sequence to a queue");

            // Wait until the graphics queue is idle:
            // TODO use a fence to submit multiple command sequences in parallel
            retCode = VK10.vkQueueWaitIdle(queue);
            Utils.checkForError(retCode, "wait for a queue to be idle");

            return this;
        }
    }
}
