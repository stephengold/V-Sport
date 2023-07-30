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
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Set;
import java.util.TreeSet;
import jme3utilities.MyString;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkMemoryType;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

/**
 * Encapsulate a Vulkan physical device, typically a graphics adapter.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class PhysicalDevice {
    // *************************************************************************
    // fields

    /**
     * encapsulated lwjgl-vulkan object
     */
    final private VkPhysicalDevice vkPhysicalDevice;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a physical device based on a handle.
     *
     * @param handle handle returned by {@code vkEnumeratePhysicalDevices()}
     * @param instance link to the lwjgl-vulkan library (not null)
     */
    PhysicalDevice(long handle, VkInstance instance) {
        this.vkPhysicalDevice = new VkPhysicalDevice(handle, instance);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Create a logical device based on this physical device.
     *
     * @param surfaceHandle the handle of the surface to use
     * @param enableDebugging true to produce debug output, false to suppress it
     * @return a new instance
     */
    VkDevice createLogicalDevice(long surfaceHandle, boolean enableDebugging) {
        QueueFamilySummary queueFamilies = summarizeFamilies(surfaceHandle);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer distinctFamilies = queueFamilies.pListDistinct(stack);
            int numDistinct = distinctFamilies.capacity();
            FloatBuffer priorities = stack.floats(0.5f);

            // queue-creation information:
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos
                    = VkDeviceQueueCreateInfo.calloc(numDistinct, stack);
            for (int queueIndex = 0; queueIndex < numDistinct; ++queueIndex) {
                VkDeviceQueueCreateInfo createInfo
                        = queueCreateInfos.get(queueIndex);
                createInfo.sType(
                        VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);

                int familyIndex = distinctFamilies.get(queueIndex);
                createInfo.queueFamilyIndex(familyIndex);
                createInfo.pQueuePriorities(priorities);
            }

            // device features:
            VkPhysicalDeviceFeatures deviceFeatures
                    = VkPhysicalDeviceFeatures.calloc(stack);
            deviceFeatures.samplerAnisotropy(true);

            // logical-device creation information:
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);

            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pEnabledFeatures(deviceFeatures);

            PointerBuffer extensionNames = listRequiredDeviceExtensions(stack);
            createInfo.ppEnabledExtensionNames(extensionNames);

            if (enableDebugging) {
                /*
                 * Ensure compatibility with older implementations that
                 * distinguish device-specific layers from instance layers.
                 */
                PointerBuffer layerNames
                        = BaseApplication.listRequiredLayers(stack);
                createInfo.ppEnabledLayerNames(layerNames);
            }

            // Create the logical device:
            VkAllocationCallbacks allocator = BaseApplication.allocator();
            PointerBuffer pPointer = stack.mallocPointer(1);
            int retCode = VK10.vkCreateDevice(
                    vkPhysicalDevice, createInfo, allocator, pPointer);
            Utils.checkForError(retCode, "create logical device");
            long deviceHandle = pPointer.get(0);
            VkDevice result
                    = new VkDevice(deviceHandle, vkPhysicalDevice, createInfo);

            return result;
        }
    }

    /**
     * Find a provided memory type with the specified properties.
     *
     * @param typeFilter a bitmask of memory types to consider
     * @param requiredProperties a bitmask of required properties
     * @return a memory-type index, or null if no match found
     */
    Integer findMemoryType(int typeFilter, int requiredProperties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Query the available memory types:
            VkPhysicalDeviceMemoryProperties memProperties
                    = VkPhysicalDeviceMemoryProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceMemoryProperties(
                    vkPhysicalDevice, memProperties);

            int numTypes = memProperties.memoryTypeCount();
            for (int typeIndex = 0; typeIndex < numTypes; ++typeIndex) {
                int bitPosition = 0x1 << typeIndex;
                if ((typeFilter & bitPosition) != 0x0) {
                    VkMemoryType memType = memProperties.memoryTypes(typeIndex);
                    int props = memType.propertyFlags();
                    if ((props & requiredProperties) == requiredProperties) {
                        return typeIndex;
                    }
                }
            }

            return null;
        }
    }

    /**
     * Find the first supported image format (for the specified tiling, with the
     * specified features) from the specified list.
     *
     * @param imageTiling either {@code VK_IMAGE_TILING_LINEAR} or
     * {@code VK_IMAGE_TILING_OPTIMAL}
     * @param requiredFeatures a bitmask of required format features
     * @param candidateFormats the list of image formats to consider, with [0]
     * being the most preferred format
     * @return an image format from the list
     */
    int findSupportedFormat(
            int imageTiling, int requiredFeatures, int... candidateFormats) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties pFormatProperties
                    = VkFormatProperties.calloc(stack);

            for (int format : candidateFormats) {
                VK10.vkGetPhysicalDeviceFormatProperties(
                        vkPhysicalDevice, format, pFormatProperties);

                int features;
                switch (imageTiling) {
                    case VK10.VK_IMAGE_TILING_LINEAR:
                        features = pFormatProperties.linearTilingFeatures();
                        break;
                    case VK10.VK_IMAGE_TILING_OPTIMAL:
                        features = pFormatProperties.optimalTilingFeatures();
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "imageTiling = " + imageTiling);
                }

                if ((features & requiredFeatures) == requiredFeatures) {
                    return format;
                }
            }
        }

        throw new RuntimeException("Failed to find a supported format");
    }

    /**
     * Return the largest number of samples per pixel that's supported for both
     * color attachments (with floating-point formats) and depth attachments.
     *
     * @return the number of samples per pixel (a power of 2, &ge;1, &le;64)
     */
    int maxNumSamples() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties
                    = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(vkPhysicalDevice, properties);
            VkPhysicalDeviceLimits limits = properties.limits();

            int bitmask = limits.framebufferColorSampleCounts()
                    & limits.framebufferDepthSampleCounts();
            if ((bitmask & VK10.VK_SAMPLE_COUNT_64_BIT) != 0x0) {
                return 64;
            } else if ((bitmask & VK10.VK_SAMPLE_COUNT_32_BIT) != 0x0) {
                return 32;
            } else if ((bitmask & VK10.VK_SAMPLE_COUNT_16_BIT) != 0x0) {
                return 16;
            } else if ((bitmask & VK10.VK_SAMPLE_COUNT_8_BIT) != 0x0) {
                return 8;
            } else if ((bitmask & VK10.VK_SAMPLE_COUNT_4_BIT) != 0x0) {
                return 4;
            } else if ((bitmask & VK10.VK_SAMPLE_COUNT_2_BIT) != 0x0) {
                return 2;
            } else {
                return 1;
            }
        }
    }

    /**
     * Rate the suitability of the device for graphics applications.
     *
     * @param surfaceHandle the surface for graphics display
     * @param diagnose true to print diagnostic messages, otherwise false
     * @return a suitability score (&gt;0, larger is more suitable) or zero if
     * unsuitable
     */
    float suitability(long surfaceHandle, boolean diagnose) {
        if (diagnose) {
            System.out.println(" Rating suitability of device " + this + ":");
        }

        // Does the device support all required extensions?
        Set<String> extensions = listAvailableExtensions();
        String[] requiredExtensions
                = BaseApplication.listRequiredDeviceExtensions();
        for (String name : requiredExtensions) {
            if (!extensions.contains(name)) {
                if (diagnose) {
                    System.out.println("  doesn't support extension " + name);
                }
                return 0f;
            }
        }

        // Does the surface provide adequate swap-chain support?
        if (!hasAdequateSwapChainSupport(surfaceHandle, diagnose)) {
            return 0f;
        }

        // Does the surface support the required queue families?
        QueueFamilySummary queueFamilies = summarizeFamilies(surfaceHandle);
        if (!queueFamilies.isComplete()) {
            if (diagnose) {
                System.out.println("  doesn't provide both queue families");
            }
            return 0f;
        }

        // Does the device support anisotropic sampling of textures?
        if (!hasAnisotropySupport()) {
            if (diagnose) {
                System.out.println(
                        "  doesn't support anisotropic sampling of textures");
            }
            return 0f;
        }

        return 1f;
    }

    /**
     * Summarize the queue families provided by the device.
     *
     * @param surfaceHandle handle of the surface for presentation
     * @return a new instance (not null)
     */
    QueueFamilySummary summarizeFamilies(long surfaceHandle) {
        QueueFamilySummary result = new QueueFamilySummary();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the available queue families:
            IntBuffer pCount = stack.mallocInt(1);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(
                    vkPhysicalDevice, pCount, null);
            int numFamilies = pCount.get(0);

            VkQueueFamilyProperties.Buffer pProperties
                    = VkQueueFamilyProperties.malloc(numFamilies, stack);
            VK10.vkGetPhysicalDeviceQueueFamilyProperties(
                    vkPhysicalDevice, pCount, pProperties);

            for (int familyI = 0; familyI < numFamilies; ++familyI) {
                VkQueueFamilyProperties family = pProperties.get(familyI);

                // Does the family provide graphics support?
                int flags = family.queueFlags();
                if ((flags & VK10.VK_QUEUE_GRAPHICS_BIT) != 0x0) {
                    result.setGraphics(familyI);
                }

                // Does the family provide presentation support?
                int retCode = KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(
                        vkPhysicalDevice, familyI, surfaceHandle, pCount);
                Utils.checkForError(retCode, "test for presentation support");
                int supportsPresentation = pCount.get(0);
                if (supportsPresentation == VK10.VK_TRUE) {
                    result.setPresentation(familyI);
                }
            }

            return result;
        }
    }

    /**
     * Summarize the properties of the specified surface on this device.
     *
     * @param surfaceHandle the handle of the surface to analyze
     * @param stack for memory allocation (not null)
     * @return a new instance containing temporary buffers (not null)
     */
    SurfaceSummary summarizeSurface(long surfaceHandle, MemoryStack stack) {
        SurfaceSummary result = new SurfaceSummary(
                vkPhysicalDevice, surfaceHandle, stack);
        return result;
    }

    /**
     * Test whether the device supports blitting with linear interpolation on
     * images with optimal tiling in the specified image format.
     *
     * @param imageFormat the image format to test
     * @return true if supported, otherwise false
     */
    boolean supportsLinearBlit(int imageFormat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties pFormatProperties
                    = VkFormatProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceFormatProperties(
                    vkPhysicalDevice, imageFormat, pFormatProperties);
            int features = pFormatProperties.optimalTilingFeatures();
            int required
                    = VK10.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;

            if ((features & required) == required) {
                return true;
            } else {
                return false;
            }
        }
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent this instance as a text string.
     *
     * @return descriptive string of text (not null)
     */
    @Override
    public String toString() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties
                    = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(vkPhysicalDevice, properties);
            String name = properties.deviceNameString();
            String result = MyString.quote(name);

            return result;
        }
    }
    // *************************************************************************
    // private methods

    /**
     * Test whether the device has adequate swap-chain support.
     *
     * @param surfaceHandle the surface to test against (not null)
     * @param diagnose true to print diagnostic messages, otherwise false
     * @return true if the support is adequate, otherwise false
     */
    private boolean hasAdequateSwapChainSupport(
            long surfaceHandle, boolean diagnose) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SurfaceSummary surface = new SurfaceSummary(
                    vkPhysicalDevice, surfaceHandle, stack);
            if (diagnose && !surface.hasFormat()) {
                System.out.println(
                        "  doesn't have any formats available");
            }
            if (diagnose && !surface.hasPresentationMode()) {
                System.out.println(
                        "  doesn't have any present modes avilable");
            }
            boolean result = surface.hasFormat()
                    && surface.hasPresentationMode();

            return result;
        }
    }

    /**
     * Test whether the device supports anisotropic sampling of textures.
     *
     * @return true if supported, otherwise false
     */
    private boolean hasAnisotropySupport() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures supportedFeatures
                    = VkPhysicalDeviceFeatures.malloc(stack);
            VK10.vkGetPhysicalDeviceFeatures(
                    vkPhysicalDevice, supportedFeatures);
            boolean result = supportedFeatures.samplerAnisotropy();

            return result;
        }
    }

    /**
     * Enumerate all available extensions for the device.
     *
     * @return a new set of extension names (not null)
     */
    private Set<String> listAvailableExtensions() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the available extensions:
            String layerName = null; // Vk implementation and implicitly enabled
            IntBuffer pCount = stack.mallocInt(1);
            int retCode = VK10.vkEnumerateDeviceExtensionProperties(
                    vkPhysicalDevice, layerName, pCount, null);
            Utils.checkForError(retCode, "count extensions");
            int numExtensions = pCount.get(0);

            VkExtensionProperties.Buffer buffer
                    = VkExtensionProperties.malloc(numExtensions, stack);
            retCode = VK10.vkEnumerateDeviceExtensionProperties(
                    vkPhysicalDevice, layerName, pCount, buffer);
            Utils.checkForError(retCode, "enumerate extensions");

            Set<String> result = new TreeSet<>();
            for (int extIndex = 0; extIndex < numExtensions; ++extIndex) {
                VkExtensionProperties properties = buffer.get(extIndex);
                String extensionName = properties.extensionNameString();
                //System.out.println("extension name = " + extensionName);
                result.add(extensionName);
            }

            return result;
        }
    }

    /**
     * Enumerate all required device extensions.
     *
     * @param stack for memory allocation (not null)
     * @return a temporary buffer containing the names of all device extensions
     * required by this application
     */
    private static PointerBuffer listRequiredDeviceExtensions(
            MemoryStack stack) {
        String[] requiredExtensions
                = BaseApplication.listRequiredDeviceExtensions();
        int numLayers = requiredExtensions.length;
        PointerBuffer result = stack.mallocPointer(numLayers);
        for (String extensionName : requiredExtensions) {
            ByteBuffer utf8Name = stack.UTF8(extensionName);
            result.put(utf8Name);
        }
        result.rewind();

        return result;
    }
}
