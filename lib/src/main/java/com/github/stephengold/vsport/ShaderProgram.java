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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Scanner;
import jme3utilities.MyString;
import jme3utilities.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

/**
 * Encapsulate a vertex shader and a fragment shader that are used together.
 */
class ShaderProgram extends DeviceResource {
    // *************************************************************************
    // fields

    /**
     * required inputs
     */
    private EnumSet<ShaderInput> requiredInputs;
    /**
     * handle of the {@code VkShaderModule} for the fragment shader
     */
    private long fragModuleHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the {@code VkShaderModule} for the vertex shader
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
     * @param programName the base name of the shaders to load (not null, not
     * empty)
     */
    ShaderProgram(String programName) {
        Validate.nonEmpty(programName, "program name");
        this.programName = programName;
        // Defer {@code VkShaderModule} creation until handles are accessed.
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Count how many vertex attributes the program requires.
     *
     * @return the count (&gt;0)
     */
    int countAttributes() {
        if (requiredInputs == null) {
            reloadRequiredInputs();
        }

        int result = 0;
        for (ShaderInput input : requiredInputs) {
            if (input.isVertexAttribute()) {
                ++result;
            }
        }

        return result;
    }

    /**
     * Return the fragment shader.
     *
     * @return the handle of a {@code VkShaderModule} (not null)
     */
    long fragModuleHandle() {
        if (fragModuleHandle == VK10.VK_NULL_HANDLE) {
            reloadFragModule();
        }

        assert fragModuleHandle != VK10.VK_NULL_HANDLE;
        return fragModuleHandle;
    }

    /**
     * Generate an attribute-description buffer.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer
     */
    VkVertexInputAttributeDescription.Buffer
            generateAttributeDescriptions(MemoryStack stack) {
        int numAttributes = countAttributes();
        VkVertexInputAttributeDescription.Buffer result
                = VkVertexInputAttributeDescription.calloc(
                        numAttributes, stack);

        int slotIndex = 0; // current binding/slot
        int offset = 0; // start offset in bytes

        // position attribute (3 signed floats in slot 0)
        VkVertexInputAttributeDescription posDescription
                = result.get(slotIndex);
        posDescription.binding(slotIndex);
        posDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
        posDescription.location(slotIndex); // slot 0 (see the vertex shader)
        posDescription.offset(offset); // start offset in bytes

        if (requires(ShaderInput.VertexColor)) {
            // color attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription colorDescription
                    = result.get(slotIndex);
            colorDescription.binding(slotIndex);
            colorDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
            colorDescription.location(slotIndex);
            colorDescription.offset(offset); // start offset in bytes
        }

        if (requires(ShaderInput.VertexNormal)) {
            // normal attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription normalDescription
                    = result.get(slotIndex);
            normalDescription.binding(slotIndex);
            normalDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
            normalDescription.location(slotIndex);
            normalDescription.offset(offset); // start offset in bytes
        }

        if (requires(ShaderInput.VertexTexCoords)) {
            // texCoords attribute (2 signed floats)
            ++slotIndex;
            VkVertexInputAttributeDescription texCoordsDescription
                    = result.get(slotIndex);
            texCoordsDescription.binding(slotIndex);
            texCoordsDescription.format(VK10.VK_FORMAT_R32G32_SFLOAT);
            texCoordsDescription.location(slotIndex);
            texCoordsDescription.offset(offset); // start offset in bytes
        }

        return result;
    }

    /**
     * Generate a binding-description buffer.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer
     */
    VkVertexInputBindingDescription.Buffer
            generateBindingDescription(MemoryStack stack) {
        int numAttributes = countAttributes();
        VkVertexInputBindingDescription.Buffer result
                = VkVertexInputBindingDescription.calloc(numAttributes, stack);

        int slotIndex = 0; // current binding/slot

        // position attribute (3 signed floats in slot 0)
        VkVertexInputBindingDescription posDescription = result.get(0);
        posDescription.binding(slotIndex);
        posDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        posDescription.stride(Mesh.numAxes * Float.BYTES);

        if (requires(ShaderInput.VertexColor)) {
            // color attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription colorDescription
                    = result.get(slotIndex);
            colorDescription.binding(slotIndex);
            colorDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            colorDescription.stride(Mesh.numAxes * Float.BYTES);
        }

        if (requires(ShaderInput.VertexNormal)) {
            // normal attribute (3 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription normalDescription
                    = result.get(slotIndex);
            normalDescription.binding(slotIndex);
            normalDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            normalDescription.stride(Mesh.numAxes * Float.BYTES);
        }

        if (requires(ShaderInput.VertexTexCoords)) {
            // texture-coordinates attribute (2 signed floats)
            ++slotIndex;
            VkVertexInputBindingDescription tcDescription
                    = result.get(slotIndex);
            tcDescription.binding(slotIndex);
            tcDescription.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
            tcDescription.stride(2 * Float.BYTES);
        }

        return result;
    }

    /**
     * Test whether the program requires the specified input.
     *
     * @param input the enum value of the input to test for (not null)
     * @return true if required, otherwise false
     */
    boolean requires(ShaderInput input) {
        if (requiredInputs == null) {
            reloadRequiredInputs();
        }
        boolean result = requiredInputs.contains(input);

        return result;
    }

    /**
     * Return the vertex shader.
     *
     * @return the handle of a {@code VkShaderModule} (not null)
     */
    long vertModuleHandle() {
        if (vertModuleHandle == VK10.VK_NULL_HANDLE) {
            reloadVertModule();
        }

        assert vertModuleHandle != VK10.VK_NULL_HANDLE;
        return vertModuleHandle;
    }
    // *************************************************************************
    // DeviceResource methods

    /**
     * Destroy all resources owned by this program.
     */
    @Override
    protected void destroy() {
        updateLogicalDevice(null);
        super.destroy();
    }

    /**
     * Callback invoked when the logical device changes.
     *
     * @param nextDevice ignored
     */
    @Override
    void updateLogicalDevice(LogicalDevice nextDevice) {
        LogicalDevice logicalDevice = Internals.getLogicalDevice();
        this.fragModuleHandle
                = logicalDevice.destroyShaderModule(fragModuleHandle);
        this.vertModuleHandle
                = logicalDevice.destroyShaderModule(vertModuleHandle);
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
     * @return the handle of the new {@code VkShaderModule} (not null)
     */
    private static long createShaderModule(ByteBuffer spirvCode) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkShaderModuleCreateInfo createInfo
                    = VkShaderModuleCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO);
            createInfo.pCode(spirvCode);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateShaderModule(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create shader module");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Load the fragment shader (FRAG file) from the classpath and compile it to
     * a {@code VkShaderModule}.
     */
    private void reloadFragModule() {
        String resourceName = String.format("/Shaders/%s.frag", programName);
        long fragSpirvHandle = SpirvUtils.compileShaderFromClasspath(
                resourceName, Shaderc.shaderc_glsl_fragment_shader);
        ByteBuffer byteCode = Shaderc.shaderc_result_get_bytes(fragSpirvHandle);
        this.fragModuleHandle = createShaderModule(byteCode);
    }

    /**
     * Load the required-input list (TXT file) from the classpath.
     */
    private void reloadRequiredInputs() {
        String resourceName = String.format("/Shaders/%s.txt", programName);
        InputStream inputStream = Utils.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }
        Scanner scanner
                = new Scanner(inputStream, StandardCharsets.UTF_8.name());

        this.requiredInputs = EnumSet.noneOf(ShaderInput.class);
        while (scanner.hasNext()) {
            String variableName = scanner.next();
            ShaderInput input = ShaderInput.find(variableName);
            if (input == null) {
                String qVariable = MyString.quote(variableName);
                String qResource = MyString.quote(resourceName);
                throw new RuntimeException("Unknown variable name:  "
                        + qVariable + " in resource " + qResource);
            }
            requiredInputs.add(input);
        }
    }

    /**
     * Load the vertex shader (VERT file) from the classpath and compile it to a
     * {@code VkShaderModule}.
     */
    private void reloadVertModule() {
        String resourceName = String.format("/Shaders/%s.vert", programName);
        long vertSpirvHandle = SpirvUtils.compileShaderFromClasspath(
                resourceName, Shaderc.shaderc_glsl_vertex_shader);
        ByteBuffer byteCode = Shaderc.shaderc_result_get_bytes(vertSpirvHandle);
        this.vertModuleHandle = createShaderModule(byteCode);
    }
}
