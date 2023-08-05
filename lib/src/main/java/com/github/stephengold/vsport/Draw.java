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
import jme3utilities.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;

/**
 * Resources required to generate the commands for a single draw: a non-global
 * UBO, a descriptor set, and a graphics pipeline.
 *
 * @author Stephen Gold sgold@sonic.net
 */
class Draw {
    // *************************************************************************
    // fields

    /**
     * per-geometry uniform buffer object (UBO)
     */
    private BufferResource nonGlobalUbo;
    /**
     * {@code VkDescriptorSet} handle
     */
    final private long descriptorSetHandle;
    /**
     * {@code VkPipeline} handle
     */
    private long pipelineHandle = VK10.VK_NULL_HANDLE;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a single draw.
     *
     * @param dsPoolHandle the handle of the {@code VkDescriptorPool} (not null)
     * @param dsLayoutHandle the handle of the {@code VkDescriptorSetLayout}
     * null)
     */
    Draw(long dsPoolHandle, long dsLayoutHandle) {
        Validate.nonNull(dsPoolHandle, "pool handle");
        Validate.nonNull(dsLayoutHandle, "layout handle");

        int numBytes = NonGlobalUniformValues.numBytes();
        int usage = VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
        boolean staging = false;
        this.nonGlobalUbo = new BufferResource(numBytes, usage, staging);

        this.descriptorSetHandle
                = allocateDescriptorSet(dsPoolHandle, dsLayoutHandle);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Access the descriptor set.
     *
     * @return the handle of the pre-existing {@code VkDescriptorSet} (not null)
     */
    long descriptorSetHandle() {
        assert descriptorSetHandle != VK10.VK_NULL_HANDLE;
        return descriptorSetHandle;
    }

    /**
     * Destroy all resources owned by this instance.
     */
    void destroy() {
        if (nonGlobalUbo != null) {
            nonGlobalUbo.destroy();
            this.nonGlobalUbo = null;
        }

        if (pipelineHandle != VK10.VK_NULL_HANDLE) {
            LogicalDevice logicalDevice = BaseApplication.getLogicalDevice();
            this.pipelineHandle = logicalDevice.destroyPipeline(pipelineHandle);
        }
    }

    /**
     * Access the per-geometry Uniform Buffer Object (UBO).
     *
     * @return the pre-existing instance (not null)
     */
    BufferResource getNonGlobalUbo() {
        assert nonGlobalUbo != null;
        return nonGlobalUbo;
    }

    /**
     * Return the pipeline handle.
     *
     * @return the handle of the pre-existing {@code VkPipeline} (not null)
     */
    long pipelineHandle() {
        assert pipelineHandle != VK10.VK_NULL_HANDLE;
        return pipelineHandle;
    }

    /**
     * Assign a different pipeline to this instance.
     *
     * @param handle the handle of the desired VkPipeline
     */
    void setPipeline(long handle) {
        if (pipelineHandle != VK10.VK_NULL_HANDLE) {
            VkDevice vkDevice = BaseApplication.getVkDevice();
            VkAllocationCallbacks allocator = BaseApplication.findAllocator();
            VK10.vkDestroyPipeline(vkDevice, pipelineHandle, allocator);
        }

        this.pipelineHandle = handle;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Allocate a descriptor set using the specified pool and layout.
     *
     * @param poolHandle the handle of the {@code VkDescriptorPool} (not null)
     * @param layoutHandle the handle of the {@code VkDescriptorSetLayout} (not
     * null)
     * @return the handle of the new {@code VkDescriptorSet}
     */
    private static long allocateDescriptorSet(
            long poolHandle, long layoutHandle) {
        assert poolHandle != VK10.VK_NULL_HANDLE;
        assert layoutHandle != VK10.VK_NULL_HANDLE;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetAllocateInfo allocInfo
                    = VkDescriptorSetAllocateInfo.calloc(stack);
            allocInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);

            allocInfo.descriptorPool(poolHandle);

            LongBuffer pLayouts = stack.longs(layoutHandle);
            allocInfo.pSetLayouts(pLayouts);

            VkDevice vkDevice = BaseApplication.getVkDevice();
            LongBuffer pSetHandles = stack.mallocLong(1);
            int retCode = VK10.vkAllocateDescriptorSets(
                    vkDevice, allocInfo, pSetHandles);
            Utils.checkForError(retCode, "allocate a descriptor set");
            long result = pSetHandles.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }
}
