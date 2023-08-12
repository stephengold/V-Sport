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

import java.nio.IntBuffer;
import org.lwjgl.system.MemoryStack;

/**
 * Summarize the queue families provided by a physical device.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the QueueFamilyIndices class in Cristian Herrera's
 * Vulkan-Tutorial-Java project.
 */
class QueueFamilySummary {
    // *************************************************************************
    // fields

    /**
     * index of the queue family to use for graphics, or null if unspecified
     */
    private Integer graphics;
    /**
     * index of the queue family to use for presentation, or null if unspecified
     */
    private Integer presentation;
    // *************************************************************************
    // new methods exposed

    /**
     * Return the queue family for graphics.
     *
     * @return the index of the family to use
     */
    int graphics() {
        return graphics;
    }

    /**
     * Test whether queue families have been specified for both graphics and
     * presentation.
     *
     * @return true if both specified, otherwise false
     */
    boolean isComplete() {
        boolean result = (graphics != null) && (presentation != null);
        return result;
    }

    /**
     * Enumerate the distinct queue families used by the application.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer of queue-family indices (not null, not
     * empty)
     */
    IntBuffer pListDistinct(MemoryStack stack) {
        assert isComplete();

        IntBuffer result;
        if (graphics == presentation) {
            result = stack.ints(graphics);
        } else {
            result = stack.ints(graphics, presentation);
        }

        return result;
    }

    /**
     * Return the queue family for presentation.
     *
     * @return the index of the queue family to use
     */
    int presentation() {
        return presentation;
    }

    /**
     * Specify the queue family for graphics.
     *
     * @param familyIndex the index of the queue family to use
     */
    void setGraphics(int familyIndex) {
        this.graphics = familyIndex;
    }

    /**
     * Specify the queue family for presentation.
     *
     * @param familyIndex the index of the queue family to use
     */
    void setPresentation(int familyIndex) {
        this.presentation = familyIndex;
    }
}
