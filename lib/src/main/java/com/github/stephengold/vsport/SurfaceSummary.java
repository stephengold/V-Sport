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
import jme3utilities.Validate;
import jme3utilities.math.MyMath;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;

/**
 * Summarize the features of a particular VkSurfaceKHR.
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
     * handle of the {@code VkSurfaceKHR} being analyzed
     */
    final private long surfaceHandle;
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
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} to analyze
     * (not null)
     * @param stack for allocating temporary host buffers (not null)
     */
    SurfaceSummary(VkPhysicalDevice physicalDevice, long surfaceHandle,
            MemoryStack stack) {
        Validate.nonNull(physicalDevice, "physical device");
        Validate.nonZero(surfaceHandle, "surface handle");

        this.surfaceHandle = surfaceHandle;

        // Obtain the capabilities of the VkSurfaceKHR:
        this.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
        int retCode = KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
                physicalDevice, surfaceHandle, capabilities);
        Utils.checkForError(retCode, "obtain surface capabilities");

        // Count the available surface formats:
        IntBuffer storeInt = stack.mallocInt(1);
        retCode = KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                physicalDevice, surfaceHandle, storeInt, null);
        Utils.checkForError(retCode, "count surface formats");
        int numFormats = storeInt.get(0);

        // Enumerate the available surface formats:
        this.formats = VkSurfaceFormatKHR.malloc(numFormats, stack);
        if (numFormats > 0) {
            retCode = KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(
                    physicalDevice, surfaceHandle, storeInt, formats);
            Utils.checkForError(retCode, "enumerate surface formats");
        }

        // Count the available surface-presentation modes:
        retCode = KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR(
                physicalDevice, surfaceHandle, storeInt, null);
        Utils.checkForError(retCode, "count presentation modes");
        int numModes = storeInt.get(0);

        // Enumerate the available surface-presentation modes:
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
     * Choose an extent for framebuffers on this surface.
     *
     * @param preferredWidth the preferred width of the frame buffer (in pixels)
     * @param preferredHeight the preferred height of the frame buffer (in
     * pixels)
     * @param storeResult storage for the result (not null, modified)
     */
    void chooseFramebufferExtent(
            int preferredWidth, int preferredHeight, VkExtent2D storeResult) {
        VkExtent2D current = capabilities.currentExtent();
        int maxUint = 0xffffffff;
        if (current.width() != maxUint || current.height() != maxUint) {
            // The framebuffer must have the same resolution as the surface:
            storeResult.set(current);
            return;
        }

        // The surface supports a range of framebuffer resolutions:
        VkExtent2D minExtent = capabilities.minImageExtent();
        VkExtent2D maxExtent = capabilities.maxImageExtent();

        int clampedWidth = MyMath.clamp(
                preferredWidth, minExtent.width(), maxExtent.width());
        int clampedHeight = MyMath.clamp(
                preferredHeight, minExtent.height(), maxExtent.height());

        storeResult.width(clampedWidth);
        storeResult.height(clampedHeight);
    }

    /**
     * Choose a presentation mode for the swap chain.
     *
     * @param enableVsync true &rarr; accept one presentation request per
     * vertical blanking period, false &rarr; accept unlimited presentation
     * requests
     * @return the selected mode
     */
    int choosePresentationMode(boolean enableVsync) {
        int preferredMode;
        if (enableVsync) {
            preferredMode = KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
        } else {
            preferredMode = KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
        }

        int numModes = presentationModes.capacity();
        for (int i = 0; i < numModes; ++i) {
            int mode = presentationModes.get(i);
            if (mode == preferredMode) {
                return mode;
            }
        }

        System.err.println("The preferred presentation mode is unavailable.");
        System.err.flush();

        // All Vulkan implementations support FIFO mode:
        return KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
    }

    /**
     * Choose a surface format for the swap chain.
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

        // Otherwise, settle for the first available surface format:
        VkSurfaceFormatKHR result = formats.get(0);
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

    /**
     * Access the underlying {@code VkSurfaceKHR}.
     *
     * @return the handle (not null)
     */
    long handle() {
        assert surfaceHandle != VK10.VK_NULL_HANDLE;
        return surfaceHandle;
    }

    /**
     * Test whether the surface has one or more surface formats available.
     *
     * @return true if formats are available, otherwise false
     */
    boolean hasFormat() {
        boolean result = formats.hasRemaining();
        return result;
    }

    /**
     * Test whether the surface has one or more presentation modes available.
     *
     * @return true if modes are available, otherwise false
     */
    boolean hasPresentationMode() {
        boolean result = presentationModes.hasRemaining();
        return result;
    }

    /**
     * Return the maximum number of swap-chain images supported.
     *
     * @return the count (&ge;2), or zero for no limit
     */
    int maxImageCount() {
        int result = capabilities.maxImageCount();

        assert result >= 0 : result;
        return result;
    }

    /**
     * Return the minimum number of swap-chain images supported.
     *
     * @return the count (&gt;0)
     */
    int minImageCount() {
        int result = capabilities.minImageCount();

        assert result >= 1 : result;
        return result;
    }
}
