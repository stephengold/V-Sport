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
import jme3utilities.math.MyMath;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

/**
 * Summarize the swap-chain features of a physical device.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the SwapChainSupportDetails class in Cristian Herrera's
 * Vulkan-Tutorial-Java project.
 */
class SurfaceSummary {
    // *************************************************************************
    // fields

    /**
     * available surface presentation modes
     */
    final private IntBuffer presentationModes;
    /**
     * available surface capabilities
     */
    final private VkSurfaceCapabilitiesKHR capabilities;
    /**
     * available surface formats
     */
    final private VkSurfaceFormatKHR.Buffer formats;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a summary of the specified device with the specified surface.
     *
     * @param physicalDevice the physical device to be used (not null,
     * unaffected)
     * @param surfaceHandle the handle of the surface to be analyzed
     * @param stack for memory allocation (not null)
     */
    SurfaceSummary(VkPhysicalDevice physicalDevice, long surfaceHandle,
            MemoryStack stack) {
        this.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                physicalDevice, surfaceHandle, capabilities);

        // Count the available surface formats:
        IntBuffer storeInt = stack.mallocInt(1);
        int retCode = KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                physicalDevice, surfaceHandle, storeInt, null);
        Utils.checkForError(retCode, "count surface formats");
        int numFormats = storeInt.get(0);

        this.formats = VkSurfaceFormatKHR.malloc(numFormats, stack);
        if (numFormats > 0) {
            KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                    physicalDevice, surfaceHandle, storeInt, formats);
        }

        // Count the available surface-presentation modes:
        retCode = KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(
                physicalDevice, surfaceHandle, storeInt, null);
        Utils.checkForError(retCode, "count presentation modes");
        int numModes = storeInt.get(0);

        this.presentationModes = stack.mallocInt(numModes);
        if (numModes > 0) {
            retCode = KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(
                    physicalDevice, surfaceHandle, storeInt, presentationModes);
            Utils.checkForError(retCode, "enumerate presentation modes");
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Choose a presentation mode for the swap chain of the main window.
     *
     * @return the selected mode
     */
    int choosePresentationMode() {
        int numModes = presentationModes.capacity();

        // Choose mailbox mode, if available:
        for (int i = 0; i < numModes; ++i) {
            int mode = presentationModes.get(i);
            if (mode == KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR) {
                return mode;
            }
        }

        // Otherwise settle for FIFO mode.
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    /**
     * Choose a surface format for the swap chain of the main window.
     *
     * @return the selected format (not null)
     */
    VkSurfaceFormatKHR chooseSurfaceFormat() {
        int numFormats = formats.capacity();
        int srgbSpace = KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;

        // Choose a 32-bit sRGB format, if available:
        for (int i = 0; i < numFormats; ++i) {
            VkSurfaceFormatKHR format = formats.get(i);
            if (format.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB
                    && format.colorSpace() == srgbSpace) {
                return format;
            }
        }

        // Otherwise settle for the first available surface format.
        VkSurfaceFormatKHR result = formats.get(0);
        return result;
    }

    /**
     * Choose an extent for the swap chain of the main window.
     *
     * @param frameBufferWidth the width of the frame buffer
     * @param frameBufferHeight the height of the frame buffer
     * @param stack for memory allocation (not null)
     * @return a new instance (may be temporary)
     */
    VkExtent2D chooseSwapExtent(
            int frameBufferWidth, int frameBufferHeight, MemoryStack stack) {
        if (capabilities.currentExtent().width() != 0xffffffffL) {
            return capabilities.currentExtent();
        }

        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();

        int clampedWidth = MyMath.clamp(
                frameBufferWidth, minExtent.width(), maxExtent.width());
        int clampedHeight = MyMath.clamp(
                frameBufferHeight, minExtent.height(), maxExtent.height());

        VkExtent2D result = VkExtent2D.malloc(stack);
        result.width(clampedWidth);
        result.height(clampedHeight);

        return result;
    }

    /**
     * Return the current transform of the surface relative to the presentation
     * engine's natural orientation.
     *
     * @return transform flag bits
     */
    int currentTransform() {
        int result = capabilities.currentTransform();
        return result;
    }

    boolean hasFormat() {
        boolean result = formats.hasRemaining();
        return result;
    }

    boolean hasPresentationMode() {
        boolean result = presentationModes.hasRemaining();
        return result;
    }

    int maxImageCount() {
        int result = capabilities.maxImageCount();
        return result;
    }

    int minImageCount() {
        int result = capabilities.minImageCount();
        return result;
    }
}
