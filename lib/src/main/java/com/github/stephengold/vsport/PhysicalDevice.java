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
import jme3utilities.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2;
import org.lwjgl.vulkan.KHRPortabilitySubset;
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
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDevicePortabilitySubsetFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkQueueFamilyProperties;

/**
 * Encapsulate a Vulkan physical device, which typically represents a GPU or
 * graphics adapter.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class PhysicalDevice {
    // *************************************************************************
    // constants

    /**
     * name of the "portability subset" device extension
     */
    final public static String portabilitySubset
            = KHRPortabilitySubset.VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME;
    // *************************************************************************
    // fields

    /**
     * true if the device supports anisotropic texture sampling, or null if not
     * determined yet
     */
    private Boolean supportsAnisotropy;
    /**
     * true if the device supports non-solid fill modes, or null if not
     * determined yet
     */
    private Boolean supportsNonSolidFill;
    /**
     * true if the device supports TriangleFan, or null if not determined yet
     */
    private Boolean supportsTriangleFan;
    /**
     * maximum number of samples the device supports for for MSAA, or null if
     * not determined yet
     */
    private Integer maxNumSamples;
    /**
     * available extensions, or null if not determined yet
     */
    private Set<String> availableExtensions;
    /**
     * name of device, or null if not determined yet
     */
    private String name;
    /**
     * underlying lwjgl-vulkan resource
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
     * Create a logical device based on the physical device.
     *
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} for
     * presentation (not null)
     * @param enableDebugging true to produce debug output, false to suppress it
     * @return a new instance
     */
    VkDevice createLogicalDevice(long surfaceHandle, boolean enableDebugging) {
        Validate.nonZero(surfaceHandle, "surface handle");

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
            deviceFeatures.fillModeNonSolid(true);
            deviceFeatures.samplerAnisotropy(true);

            // logical-device creation information:
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);

            createInfo.pQueueCreateInfos(queueCreateInfos);
            createInfo.pEnabledFeatures(deviceFeatures);

            PointerBuffer extensionNames = listRequiredDeviceExtensions(stack);
            if (hasExtension(portabilitySubset)) {
                extensionNames = Utils.appendStringPointer(
                        extensionNames, portabilitySubset, stack);
            }
            createInfo.ppEnabledExtensionNames(extensionNames);

            if (enableDebugging) {
                /*
                 * Ensure compatibility with older implementations that
                 * distinguish device-specific layers from instance layers.
                 */
                PointerBuffer layerNames = Internals.listRequiredLayers(stack);
                createInfo.ppEnabledLayerNames(layerNames);
            }

            // Create the logical device:
            VkAllocationCallbacks allocator = Internals.findAllocator();
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
        if (maxNumSamples == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                queryProperties(stack);
            }
        }

        assert maxNumSamples >= 1 : maxNumSamples;
        assert maxNumSamples <= 64 : maxNumSamples;
        return maxNumSamples;
    }

    /**
     * Return the name of the device.
     *
     * @return the name (not null)
     */
    String name() {
        if (name == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                queryProperties(stack);
            }
        }

        assert name != null;
        return name;
    }

    /**
     * Rate the suitability of the device for graphics applications.
     *
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} for
     * presentation (not null)
     * @param diagnose true to print diagnostic messages, otherwise false
     * @return a suitability score (&gt;0, larger is more suitable) or zero if
     * unsuitable
     */
    float suitability(long surfaceHandle, boolean diagnose) {
        Validate.nonZero(surfaceHandle, "surface handle");
        if (diagnose) {
            System.out.println(" Rating suitability of device " + this + ":");
        }

        // Does the device support all required extensions?
        String[] requiredExtensions = Internals.listRequiredDeviceExtensions();
        for (String name : requiredExtensions) {
            if (!hasExtension(name)) {
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
        if (!supportsAnisotropy()) {
            if (diagnose) {
                System.out.println(
                        "  doesn't support anisotropic sampling of textures");
            }
            return 0f;
        }

        if (!supportsNonSolidFill()) {
            if (diagnose) {
                System.out.println("  doesn't support polygon fill modes"
                        + " other than solid");
            }
            return 0f;
        }

        if (!supportsTriangleFan()) {
            if (diagnose) {
                System.out.println("  doesn't support triangle-fan topology");
            }
            return 0.5f;
        }

        return 1f;
    }

    /**
     * Summarize the queue families provided by the device.
     *
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} for
     * presentation (not null)
     * @return a new instance (not null)
     */
    QueueFamilySummary summarizeFamilies(long surfaceHandle) {
        Validate.nonZero(surfaceHandle, "surface handle");

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
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} to analyze
     * (not null)
     * @param stack for allocating temporary host buffers (not null)
     * @return a new instance containing temporary buffers (not null)
     */
    SurfaceSummary summarizeSurface(long surfaceHandle, MemoryStack stack) {
        Validate.nonZero(surfaceHandle, "surface handle");

        SurfaceSummary result
                = new SurfaceSummary(vkPhysicalDevice, surfaceHandle, stack);
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

    /**
     * Test whether the device supports the TriangleFan primitive topology.
     *
     * @return true if supported, otherwise false
     */
    boolean supportsTriangleFan() {
        if (supportsTriangleFan == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                if (hasExtension(portabilitySubset)) {
                    querySubsetFeatures(stack);
                } else {
                    this.supportsTriangleFan = true;
                }
            }
        }

        return supportsTriangleFan;
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
        String result = MyString.quote(name);
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Test whether the device has adequate swap-chain support.
     *
     * @param surfaceHandle the handle of the {@code VkSurfaceKHR} to for
     * presentation (not null)
     * @param diagnose true to print diagnostic messages, otherwise false
     * @return true if the support is adequate, otherwise false
     */
    private boolean hasAdequateSwapChainSupport(
            long surfaceHandle, boolean diagnose) {
        Validate.nonZero(surfaceHandle, "surface handle");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            SurfaceSummary surface = new SurfaceSummary(
                    vkPhysicalDevice, surfaceHandle, stack);
            if (diagnose && !surface.hasFormat()) {
                System.out.println("  doesn't have any formats available");
            }
            if (diagnose && !surface.hasPresentationMode()) {
                System.out.println("  doesn't have any present modes avilable");
            }
            boolean result
                    = surface.hasFormat() && surface.hasPresentationMode();

            return result;
        }
    }

    /**
     * Test whether the named device extension is available.
     *
     * @param extensionName the name of the extension to test for (not null, not
     * empty)
     * @return true if available, otherwise false
     */
    private boolean hasExtension(String extensionName) {
        if (availableExtensions == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                queryExtensionProperties(stack);
            }
        }

        if (availableExtensions.contains(extensionName)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Enumerate all device extensions required by V-Sport.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a temporary buffer containing the names of all device extensions
     * required by the application
     */
    private static PointerBuffer listRequiredDeviceExtensions(
            MemoryStack stack) {
        String[] requiredExtensions = Internals.listRequiredDeviceExtensions();
        int numLayers = requiredExtensions.length;
        PointerBuffer result = stack.mallocPointer(numLayers);
        for (String extensionName : requiredExtensions) {
            ByteBuffer utf8Name = stack.UTF8(extensionName);
            result.put(utf8Name);
        }
        result.rewind();

        return result;
    }

    /**
     * Enumerate all available extensions for the device.
     *
     * @param stack for allocating temporary host buffers (not null)
     */
    private void queryExtensionProperties(MemoryStack stack) {
        // Count the available extensions:
        String layerName = null; // Vk implementation and implicitly enabled
        IntBuffer pCount = stack.mallocInt(1);
        int retCode = VK10.vkEnumerateDeviceExtensionProperties(
                vkPhysicalDevice, layerName, pCount, null);
        Utils.checkForError(retCode, "count extensions");
        int numExtensions = pCount.get(0);

        VkExtensionProperties.Buffer pProperties
                = VkExtensionProperties.malloc(numExtensions, stack);
        retCode = VK10.vkEnumerateDeviceExtensionProperties(
                vkPhysicalDevice, layerName, pCount, pProperties);
        Utils.checkForError(retCode, "enumerate extensions");

        this.availableExtensions = new TreeSet<>();
        for (int extIndex = 0; extIndex < numExtensions; ++extIndex) {
            VkExtensionProperties properties = pProperties.get(extIndex);
            String extensionName = properties.extensionNameString();
            availableExtensions.add(extensionName);
        }
    }

    /**
     * Query the basic features of the device, including support for anisotropic
     * sampling of textures and non-solid fill modes for polygons.
     *
     * @param stack for allocating temporary host buffers (not null)
     */
    private void queryFeatures(MemoryStack stack) {
        VkPhysicalDeviceFeatures supportedFeatures
                = VkPhysicalDeviceFeatures.malloc(stack);
        VK10.vkGetPhysicalDeviceFeatures(vkPhysicalDevice, supportedFeatures);
        this.supportsAnisotropy = supportedFeatures.samplerAnisotropy();
        this.supportsNonSolidFill = supportedFeatures.fillModeNonSolid();
    }

    /**
     * Query the basic properties of the device, including its limits and name.
     *
     * @param stack for allocating temporary host buffers (not null)
     */
    private void queryProperties(MemoryStack stack) {
        VkPhysicalDeviceProperties properties
                = VkPhysicalDeviceProperties.calloc(stack);
        VK10.vkGetPhysicalDeviceProperties(vkPhysicalDevice, properties);

        this.name = properties.deviceNameString();

        VkPhysicalDeviceLimits limits = properties.limits();
        int bitmask = limits.framebufferColorSampleCounts()
                & limits.framebufferDepthSampleCounts();
        if ((bitmask & VK10.VK_SAMPLE_COUNT_64_BIT) != 0x0) {
            this.maxNumSamples = 64;
        } else if ((bitmask & VK10.VK_SAMPLE_COUNT_32_BIT) != 0x0) {
            this.maxNumSamples = 32;
        } else if ((bitmask & VK10.VK_SAMPLE_COUNT_16_BIT) != 0x0) {
            this.maxNumSamples = 16;
        } else if ((bitmask & VK10.VK_SAMPLE_COUNT_8_BIT) != 0x0) {
            this.maxNumSamples = 8;
        } else if ((bitmask & VK10.VK_SAMPLE_COUNT_4_BIT) != 0x0) {
            this.maxNumSamples = 4;
        } else if ((bitmask & VK10.VK_SAMPLE_COUNT_2_BIT) != 0x0) {
            this.maxNumSamples = 2;
        } else {
            this.maxNumSamples = 1;
        }
    }

    /**
     * Query the portability-subset features of the device, including
     * triangle-fan support.
     *
     * @param stack for allocating temporary host buffers (not null)
     */
    private void querySubsetFeatures(MemoryStack stack) {
        VkPhysicalDeviceFeatures2 features2
                = VkPhysicalDeviceFeatures2.calloc(stack);
        VkPhysicalDevicePortabilitySubsetFeaturesKHR psFeatures
                = VkPhysicalDevicePortabilitySubsetFeaturesKHR.calloc(stack);
        features2.pNext(psFeatures);
        KHRGetPhysicalDeviceProperties2.vkGetPhysicalDeviceFeatures2KHR(
                vkPhysicalDevice, features2);
        this.supportsTriangleFan = psFeatures.triangleFans();
    }

    /**
     * Test whether the device supports anisotropic filtering in texture
     * samplers.
     *
     * @return true if supported, otherwise false
     */
    private boolean supportsAnisotropy() {
        if (supportsAnisotropy == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                queryFeatures(stack);
            }
        }

        return supportsAnisotropy;
    }

    /**
     * Test whether the device supports polygon fill modes other than solid,
     * such as wireframe.
     *
     * @return true if supported, otherwise false
     */
    private boolean supportsNonSolidFill() {
        if (supportsNonSolidFill == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                queryFeatures(stack);
            }
        }

        return supportsNonSolidFill;
    }
}
