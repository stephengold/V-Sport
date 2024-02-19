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
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;

/**
 * Encapsulate the synchronization objects of a single frame in flight.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Frame.java in Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class Frame {
    // *************************************************************************
    // fields

    /**
     * handle of the fence used by the GPU to signal completion of a frame
     */
    private long fenceHandle;
    /**
     * handle of the binary semaphore that signals when the image has been
     * acquired from the swapchain and is available for rendering
     */
    private long imageAvailableSemaphoreHandle;
    /**
     * handle of the binary semaphore that signals when the image has finished
     * rendering and can be presented to the surface
     */
    private long renderFinishedSemaphoreHandle;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a group of synchronization objects.
     */
    Frame() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pHandle = stack.mallocLong(1);

            // Create a fence in the signaled state:
            VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(stack);
            fenceCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

            fenceCreateInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            int retCode = VK10.vkCreateFence(
                    vkDevice, fenceCreateInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create fence");
            this.fenceHandle = pHandle.get(0);
            assert fenceHandle != VK10.VK_NULL_HANDLE;

            LogicalDevice logicalDevice = Internals.getLogicalDevice();
            this.imageAvailableSemaphoreHandle
                    = logicalDevice.createSemaphore();
            this.renderFinishedSemaphoreHandle
                    = logicalDevice.createSemaphore();
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy the synchronization objects.
     */
    void destroy() {
        VkDevice vkDevice = Internals.getVkDevice();
        VkAllocationCallbacks allocator = Internals.findAllocator();

        if (fenceHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFence(vkDevice, fenceHandle, allocator);
            this.fenceHandle = VK10.VK_NULL_HANDLE;
        }

        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        this.imageAvailableSemaphoreHandle
                = logicalDevice.destroySemaphore(imageAvailableSemaphoreHandle);
        this.renderFinishedSemaphoreHandle
                = logicalDevice.destroySemaphore(renderFinishedSemaphoreHandle);
    }

    /**
     * Access the fence for the GPU to signal completion of a frame.
     *
     * @return the handle of the pre-existing {@code VkFence} (not null)
     */
    long fenceHandle() {
        assert fenceHandle != VK10.VK_NULL_HANDLE;
        return fenceHandle;
    }

    /**
     * Access the image-available semaphore.
     *
     * @return the handle of the pre-existing {@code VkSemaphore} (not null)
     */
    long imageAvailableSemaphoreHandle() {
        assert imageAvailableSemaphoreHandle != VK10.VK_NULL_HANDLE;
        return imageAvailableSemaphoreHandle;
    }

    /**
     * Access the render-finished semaphore.
     *
     * @return the handle of the pre-existing {@code VkSemaphore} (not null)
     */
    long renderFinishedSemaphoreHandle() {
        assert renderFinishedSemaphoreHandle != VK10.VK_NULL_HANDLE;
        return renderFinishedSemaphoreHandle;
    }

    /**
     * Make the host wait for the GPU to signal the fence.
     */
    void waitForFence() {
        VkDevice vkDevice = Internals.getVkDevice();
        boolean waitAll = true;
        int retCode = VK10.vkWaitForFences(
                vkDevice, fenceHandle, waitAll, Internals.noTimeout);
        Utils.checkForError(retCode, "wait for fence");
    }
}
