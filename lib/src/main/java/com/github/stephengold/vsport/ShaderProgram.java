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

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

/**
 * Encapsulate a vertex shader and a fragment shader.
 */
class ShaderProgram {
    // *************************************************************************
    // fields

    /**
     * handle of the VkShaderModule for the fragment shader
     */
    private long fragModuleHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the VkShaderModule for the vertex shader
     */
    private long vertModuleHandle = VK10.VK_NULL_HANDLE;
    /**
     * base name of the shader files
     */
    final private String programName;
    // *************************************************************************
    // constructors

    /**
     * Instantiate the named program.
     *
     * @param programName the base name of the shaders to load (not null)
     */
    ShaderProgram(String programName) {
        assert programName != null;
        this.programName = programName;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Destroy all owned resources.
     */
    void destroy() {
        VkDevice logicalDevice = BaseApplication.getVkDevice();
        VkAllocationCallbacks allocator = BaseApplication.findAllocator();

        // Destroy the shader modules:
        if (vertModuleHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyShaderModule(
                    logicalDevice, vertModuleHandle, allocator);
            this.vertModuleHandle = VK10.VK_NULL_HANDLE;
        }
        if (fragModuleHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyShaderModule(
                    logicalDevice, fragModuleHandle, allocator);
            this.fragModuleHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Return the module for the fragment shader.
     *
     * @return the handle of the VkShaderModule
     */
    long fragModuleHandle() {
        if (fragModuleHandle == VK10.VK_NULL_HANDLE) {
            updateFragModule();
        }

        assert fragModuleHandle != VK10.VK_NULL_HANDLE;
        return fragModuleHandle;
    }

    /**
     * Return the module for the vertex shader.
     *
     * @return the handle of the VkShaderModule
     */
    long vertModuleHandle() {
        if (vertModuleHandle == VK10.VK_NULL_HANDLE) {
            updateVertModule();
        }

        assert vertModuleHandle != VK10.VK_NULL_HANDLE;
        return vertModuleHandle;
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent this program as a String.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String result = programName;
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Create a shader module from the specified SPIR-V bytecodes.
     *
     * @param spirvCode the bytecodes to use (not null)
     * @return the handle of the new module
     */
    private static long createShaderModule(ByteBuffer spirvCode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo
                    = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            VkDevice logicalDevice = BaseApplication.getVkDevice();
            VkAllocationCallbacks allocator = BaseApplication.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateShaderModule(
                    logicalDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create shader module");
            long result = pHandle.get(0);

            return result;
        }
    }

    private void updateFragModule() {
        String resourceName = String.format("/Shaders/%s.frag", programName);
        long fragSpirvHandle = SpirvUtils.compileShaderFromClasspath(
                resourceName, Shaderc.shaderc_glsl_fragment_shader);
        ByteBuffer byteCode = Shaderc.shaderc_result_get_bytes(fragSpirvHandle);
        this.fragModuleHandle = createShaderModule(byteCode);
    }

    private void updateVertModule() {
        String resourceName = String.format("/Shaders/%s.vert", programName);
        long vertSpirvHandle = SpirvUtils.compileShaderFromClasspath(
                resourceName, Shaderc.shaderc_glsl_vertex_shader);
        ByteBuffer byteCode = Shaderc.shaderc_result_get_bytes(vertSpirvHandle);
        this.vertModuleHandle = createShaderModule(byteCode);
    }
}
