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
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

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
     * Instantiate a group of synchronization objects.
     */
    Frame() {
        this.logicalDevice = BaseApplication.logicalDevice();
        this.allocator = BaseApplication.allocator();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pHandle = stack.mallocLong(1);

            // Create a fence in the signaled state:
            VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc(stack);
            fenceCreateInfo.sType(VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO);

            fenceCreateInfo.flags(VK10.VK_FENCE_CREATE_SIGNALED_BIT);

            int retCode = VK10.vkCreateFence(
                    logicalDevice, fenceCreateInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create fence");
            this.fenceHandle = pHandle.get(0);

            VkSemaphoreCreateInfo semaphoreCreateInfo
                    = VkSemaphoreCreateInfo.calloc(stack);
            semaphoreCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

            // Create the image-available semaphore in the unsignaled state:
            retCode = VK10.vkCreateSemaphore(
                    logicalDevice, semaphoreCreateInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create semaphore");
            this.imageAvailableSemaphoreHandle = pHandle.get(0);

            // Create the render-finished semaphore in the unsignaled state:
            retCode = VK10.vkCreateSemaphore(
                    logicalDevice, semaphoreCreateInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create semaphore");
            this.renderFinishedSemaphoreHandle = pHandle.get(0);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy the synchronization objects.
     */
    void destroy() {
        if (fenceHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyFence(logicalDevice, fenceHandle, allocator);
            this.fenceHandle = VK10.VK_NULL_HANDLE;
        }

        if (imageAvailableSemaphoreHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroySemaphore(
                    logicalDevice, imageAvailableSemaphoreHandle, allocator);
            this.imageAvailableSemaphoreHandle = VK10.VK_NULL_HANDLE;
        }

        if (renderFinishedSemaphoreHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroySemaphore(
                    logicalDevice, renderFinishedSemaphoreHandle, allocator);
            this.renderFinishedSemaphoreHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Return the fence for the GPU to signal after completing a frame.
     *
     * @return the handle of the fence
     */
    long fenceHandle() {
        return fenceHandle;
    }

    /**
     * Return the image-available semaphore.
     *
     * @return the handle of the semaphore
     */
    long imageAvailableSemaphoreHandle() {
        return imageAvailableSemaphoreHandle;
    }

    /**
     * Return the render-finished semaphore.
     *
     * @return the handle of the semaphore
     */
    long renderFinishedSemaphoreHandle() {
        return renderFinishedSemaphoreHandle;
    }

    /**
     * Make the host wait for the GPU to signal the fence.
     */
    void waitForFence() {
        boolean waitAll = true;
        int retCode = VK10.vkWaitForFences(logicalDevice, fenceHandle, waitAll,
                BaseApplication.noTimeout);
        Utils.checkForError(retCode, "wait for fence");
    }
}
