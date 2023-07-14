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
import org.joml.Vector2fc;
import org.joml.Vector3fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

/**
 * The attributes of a single vertex in a mesh.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the Vertex class in Cristian Herrera's Vulkan-Tutorial-Java
 * project.
 */
class Vertex {
    // *************************************************************************
    // fields

    final private Vector3fc pos;
    final private Vector2fc texCoords;
    final private Vector3fc color;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a mesh vertex from attribute values.
     *
     * @param pos the desired location of the vertex (in model coordinates)
     * @param color the desired color of the vertex
     * @param texCoords the desired texture coordinates of the vertex
     */
    Vertex(Vector3fc pos, Vector3fc color, Vector2fc texCoords) {
        this.pos = pos;
        this.color = color;
        this.texCoords = texCoords;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Generate an attribute-description buffer.
     *
     * @param stack for memory allocation (not null)
     * @return a new temporary buffer
     */
    static VkVertexInputAttributeDescription.Buffer
            createAttributeDescriptions(MemoryStack stack) {
        VkVertexInputAttributeDescription.Buffer result
                = VkVertexInputAttributeDescription.calloc(3, stack);

        // position attribute (3 signed floats in slot 0)
        VkVertexInputAttributeDescription posDescription = result.get(0);
        posDescription.binding(0);
        posDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
        posDescription.location(0); // slot 0 (see the vertex shader)
        posDescription.offset(0); // start offset in bytes

        // color attribute (3 signed floats in slot 1)
        VkVertexInputAttributeDescription colorDescription = result.get(1);
        colorDescription.binding(1);
        colorDescription.format(VK10.VK_FORMAT_R32G32B32_SFLOAT);
        colorDescription.location(1); // slot 1 (see the vertex shader)
        colorDescription.offset(0); // start offset in bytes

        // texCoords attribute (2 signed floats in slot 2)
        VkVertexInputAttributeDescription texCoordsDescription = result.get(2);
        texCoordsDescription.binding(2);
        texCoordsDescription.format(VK10.VK_FORMAT_R32G32_SFLOAT);
        texCoordsDescription.location(2); // slot 2 (see the vertex shader)
        texCoordsDescription.offset(0); // start offset in bytes

        return result;
    }

    /**
     * Generate a binding-description buffer.
     *
     * @param stack for memory allocation (not null)
     * @return a new temporary buffer
     */
    static VkVertexInputBindingDescription.Buffer
            createBindingDescription(MemoryStack stack) {
        VkVertexInputBindingDescription.Buffer result
                = VkVertexInputBindingDescription.calloc(3, stack);

        // Describe the first slot:
        VkVertexInputBindingDescription pos = result.get(0);
        pos.binding(0);
        pos.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        pos.stride(3 * Float.BYTES);

        // Describe the 2nd slot:
        VkVertexInputBindingDescription color = result.get(1);
        color.binding(1);
        color.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        color.stride(3 * Float.BYTES);

        // Describe the 3rd slot:
        VkVertexInputBindingDescription texCoords = result.get(2);
        texCoords.binding(2);
        texCoords.inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);
        texCoords.stride(2 * Float.BYTES);

        return result;
    }

    /**
     * Write the vertex color data to the specified ByteBuffer, starting at the
     * current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeColorsTo(ByteBuffer target) {
        target.putFloat(color.x());
        target.putFloat(color.y());
        target.putFloat(color.z());
    }

    /**
     * Write the vertex position data to the specified ByteBuffer, starting at
     * the current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writePositionsTo(ByteBuffer target) {
        target.putFloat(pos.x());
        target.putFloat(pos.y());
        target.putFloat(pos.z());
    }

    /**
     * Write the texture coordinate data to the specified ByteBuffer, starting
     * at the current buffer position) and advances the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeTexCoordsTo(ByteBuffer target) {
        target.putFloat(texCoords.x());
        target.putFloat(texCoords.y());
    }
}
