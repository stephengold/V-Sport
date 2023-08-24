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

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jme3utilities.Validate;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.Callback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRGetPhysicalDeviceProperties2;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;

/**
 * Utility methods and static data that are internal to the V-Sport library,
 * mostly Vulkan-specific stuff.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
final class Internals {
    // *************************************************************************
    // constants

    /**
     * true to enable debugging output and optional runtime checks, or false to
     * disable them
     */
    final private static boolean enableDebugging = false;
    /**
     * maximum number of frames in flight
     */
    final private static int maxFramesInFlight = 2;
    /**
     * timeout period for synchronization (in nanoseconds)
     */
    final static long noTimeout = 0xFFFFFFFFFFFFFFFFL;
    /**
     * allocator for host memory (null indicates the default allocator)
     */
    final private static VkAllocationCallbacks defaultAllocator = null;
    // *************************************************************************
    // fields

    /**
     * true if the framebuffers need to be resized
     */
    private static boolean needsResize = false;
    /**
     * print Vulkan debugging information (typically to the console) or null if
     * not created
     */
    private static Callback debugMessengerCallback;
    /**
     * resources that depend on the swap chain
     */
    private static ChainResources chainResources;
    /**
     * synchronization for frames in flight
     */
    private static Frame[] inFlightFrames;
    /**
     * shader parameters to be written to global UBOs
     */
    final private static GlobalUniformValues globalUniformValues
            = new GlobalUniformValues();
    /**
     * index of the frame being rendered (among the inFlightFrames)
     */
    private static int currentFrameIndex;
    /**
     * image format for the depth buffer
     */
    private static int depthBufferFormat;
    /**
     * height of the displayed frame buffer (in pixels)
     */
    private static int framebufferHeight = 600;
    /**
     * width of the displayed frame buffer (in pixels)
     */
    private static int framebufferWidth = 800;
    /**
     * samples per framebuffer pixel for multi-sample anti-aliasing (MSAA)
     */
    private static int numMsaaSamples;
    /**
     * logical device for resource creation/destruction
     */
    private static LogicalDevice logicalDevice;
    /**
     * {@code VkDescriptorSetLayout} handle
     */
    private static long descriptorSetLayoutHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the graphics-pipeline layout
     */
    private static long pipelineLayoutHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the {@code VkSurfaceKHR} for presentation
     */
    private static long surfaceHandle = VK10.VK_NULL_HANDLE;
    /**
     * map presentation-image indices to frames
     */
    private static Map<Integer, Frame> framesInFlight;
    /**
     * physical device to display the window
     */
    private static PhysicalDevice physicalDevice;
    /**
     * names of all device extensions that the application requires
     */
    final private static Set<String> requiredDeviceExtensions = new HashSet<>();
    /**
     * names of validation layers to enable during initialization
     */
    final private static Set<String> requiredLayers = new HashSet<>();
    /**
     * current background color
     */
    final private static Vector4f backgroundColor
            = new Vector4f(0f, 0f, 0f, 1f);
    /**
     * logical device for resource creation/destruction
     */
    private static VkDevice vkDevice;
    /**
     * link the application to the lwjgl-vulkan library
     */
    private static VkInstance vkInstance;
    /**
     * queue for executing graphics commands
     */
    private static VkQueue graphicsQueue;
    /**
     * presentation queue
     */
    private static VkQueue presentationQueue;
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private Internals() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Convert the specified image from one layout to another. TODO move to
     * DeviceImage
     *
     * @param image the image to convert (not null)
     * @param format the image format
     * @param oldLayout the pre-existing layout
     * @param newLayout the desired layout
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     */
    static void alterImageLayout(DeviceImage image, int format, int oldLayout,
            int newLayout, int numMipLevels) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer pBarrier
                    = VkImageMemoryBarrier.calloc(1, stack);
            pBarrier.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);

            pBarrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            pBarrier.newLayout(newLayout);
            pBarrier.oldLayout(oldLayout);
            pBarrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);

            long imageHandle = image.imageHandle();
            pBarrier.image(imageHandle);

            int aspectMask;
            if (newLayout
                    == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                aspectMask = VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
                if (Utils.hasStencilComponent(format)) {
                    aspectMask |= VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
                }
            } else {
                aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
            }

            VkImageSubresourceRange range = pBarrier.subresourceRange();
            range.aspectMask(aspectMask);
            range.baseArrayLayer(0);
            range.baseMipLevel(0);
            range.layerCount(1);
            range.levelCount(numMipLevels);

            int sourceStage;
            int destinationStage;

            if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
                // UNDEFINED to TRANSFER_DST
                pBarrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                // TRANSFER_DST to SHADER_READ_ONLY
                pBarrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
                // UNDEFINED to COLOR_ATTACHMENT
                pBarrier.dstAccessMask(
                        VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage
                        = VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL) {
                // UNDEFINED to DEPTH_STENCIL_ATTACHMENT
                pBarrier.dstAccessMask(
                        VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage
                        = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;

            } else {
                throw new IllegalArgumentException(
                        "Unsupported transition from layout=" + oldLayout
                        + " to layout=" + newLayout);
            }

            SingleUse commandSequence = new SingleUse();
            commandSequence.addBarrier(sourceStage, destinationStage, pBarrier);
            commandSequence.submitToGraphicsQueue();
        }
    }

    static void cleanUpVulkan() {
        if (logicalDevice != null) {
            // Await completion of all GPU operations:
            logicalDevice.waitIdle();
        }
        /*
         * Destroy resources in the reverse of the order they were created,
         * starting with the chain resources:
         */
        destroyChainResources();

        // Destroy the pipeline layout:
        if (pipelineLayoutHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipelineLayout(
                    vkDevice, pipelineLayoutHandle, defaultAllocator);
            pipelineLayoutHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the descriptor-set layout that's used to configure pipelines:
        if (descriptorSetLayoutHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorSetLayout(
                    vkDevice, descriptorSetLayoutHandle, defaultAllocator);
            descriptorSetLayoutHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the logical device:
        vkDevice = null;
        if (logicalDevice != null) {
            logicalDevice = logicalDevice.destroy();
        }

        // Destroy the surface:
        if (surfaceHandle != VK10.VK_NULL_HANDLE) {
            KHRSurface.vkDestroySurfaceKHR(
                    vkInstance, surfaceHandle, defaultAllocator);
            surfaceHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the Vulkan instance:
        if (vkInstance != null) {
            VK10.vkDestroyInstance(vkInstance, defaultAllocator);
            vkInstance = null;
        }

        if (debugMessengerCallback != null) {
            debugMessengerCallback.free();
            debugMessengerCallback = null;
        }
    }

    /**
     * Copy the current background color to a new FloatBuffer.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer (not null)
     */
    static FloatBuffer copyBackgroudColor(MemoryStack stack) {
        FloatBuffer result = stack.mallocFloat(4);
        backgroundColor.get(result);

        return result;
    }

    /**
     * Return the number of samples per framebuffer pixel for multi-sample
     * anti-aliasing (MSAA).
     *
     * @return the count (a power of 2, &ge;1, &le;64)
     */
    static int countMsaaSamples() {
        return numMsaaSamples;
    }

    /**
     * Return the allocator for direct buffers.
     *
     * @return the pre-existing instance, or null to use the default allocator
     */
    static VkAllocationCallbacks findAllocator() {
        return defaultAllocator;
    }

    /**
     * Return the height of the framebuffers.
     *
     * @return the height (in pixels, &ge;0)
     */
    static int framebufferHeight() {
        assert framebufferHeight >= 0 : framebufferHeight;
        return framebufferHeight;
    }

    /**
     * Return the width of the framebuffers.
     *
     * @return the width (in pixels, &ge;0)
     */
    static int framebufferWidth() {
        assert framebufferWidth >= 0 : framebufferWidth;
        return framebufferWidth;
    }

    /**
     * Access the global uniform values.
     *
     * @return the pre-existing instance (not null)
     */
    static GlobalUniformValues getGlobalUniformValues() {
        assert globalUniformValues != null;
        return globalUniformValues;
    }

    /**
     * Access the graphics queue for commands.
     *
     * @return the pre-existing instance (not null)
     */
    static VkQueue getGraphicsQueue() {
        assert graphicsQueue != null;
        return graphicsQueue;
    }

    /**
     * Access the logical device for resource creation/destruction.
     *
     * @return the pre-existing instance (not null)
     */
    static LogicalDevice getLogicalDevice() {
        assert logicalDevice != null;
        return logicalDevice;
    }

    /**
     * Access the physical device for rendering.
     *
     * @return the pre-existing instance (not null)
     */
    static PhysicalDevice getPhysicalDevice() {
        assert physicalDevice != null;
        return physicalDevice;
    }

    /**
     * Access the logical device for resource creation/destruction.
     *
     * @return the pre-existing instance (not null)
     */
    static VkDevice getVkDevice() {
        assert vkDevice != null;
        return vkDevice;
    }

    /**
     * Initialize the Vulkan API.
     *
     * @param appName the name of the application (may be null)
     * @param appVersion the application's version numbers
     * @param app the application instance (not null)
     */
    static void initializeVulkan(
            String appName, int appVersion, BaseApplication app) {
        requiredDeviceExtensions
                .add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        if (enableDebugging) {
            requiredLayers.add("VK_LAYER_KHRONOS_validation");
        }

        createVkInstance(appName, appVersion);
        surfaceHandle = createSurface();

        selectPhysicalDevice(app);

        depthBufferFormat = chooseDepthBufferFormat();
        System.out.println("depthBufferFormat = " + depthBufferFormat);
        numMsaaSamples = physicalDevice.maxNumSamples();
        System.out.println("numSamples = " + numMsaaSamples);

        createLogicalDevice();
        descriptorSetLayoutHandle = logicalDevice.createDescriptorSetLayout();
        pipelineLayoutHandle
                = logicalDevice.createPipelineLayout(descriptorSetLayoutHandle);

        createChainResources();
    }

    /**
     * Test whether the debugging aids should be enabled.
     *
     * @return true if enabled, otherwise false
     */
    static boolean isDebuggingEnabled() {
        return enableDebugging;
    }

    /**
     * Enumerate all device extensions required by the application.
     *
     * @return a new array of extension names (no duplicates, not null)
     */
    static String[] listRequiredDeviceExtensions() {
        int numExtensions = requiredDeviceExtensions.size();
        String[] result = new String[numExtensions];

        int nameIndex = 0;
        for (String name : requiredDeviceExtensions) {
            result[nameIndex] = name;
            ++nameIndex;
        }

        return result;
    }

    /**
     * Enumerate all required validation layers.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a temporary buffer containing the names of the validation layers
     */
    static PointerBuffer listRequiredLayers(MemoryStack stack) {
        int numLayers = requiredLayers.size();
        PointerBuffer result = stack.mallocPointer(numLayers);
        for (String layerName : requiredLayers) {
            ByteBuffer utf8Name = stack.UTF8(layerName);
            result.put(utf8Name);
        }
        result.rewind();

        return result;
    }

    /**
     * Render the next frame.
     */
    static void renderNextFrame() {
        // Wait for the device to signal completion of the previous frame:
        Frame frame = inFlightFrames[currentFrameIndex];
        frame.waitForFence();

        // Acquire the next presentation image from the swapchain:
        Integer imageIndex = nextPresentationImage(frame);
        if (imageIndex == null) {
            recreateChainResources();
            return;
        }

        Pass pass = chainResources.getPass(imageIndex);
        List<Geometry> geometryList = pass.getGeometryList();
        geometryList.clear();

        // List the depth-test geometries first and defer the rest:
        Collection<Geometry> visibleGeometries = BaseApplication.listVisible();
        Deque<Geometry> deferredQueue = BaseApplication.listDeferred();
        for (Geometry geometry : visibleGeometries) {
            if (geometry.isDepthTest()) {
                geometry.updateAndRender();
                assert geometry.isDepthTest();
                geometryList.add(geometry);
            } else {
                assert deferredQueue.contains(geometry);
            }
        }

        // List the no-depth-test geometries last, from back to front:
        for (Geometry geometry : deferredQueue) {
            assert visibleGeometries.contains(geometry);
            assert !geometry.isDepthTest();
            geometry.updateAndRender();
            assert !geometry.isDepthTest();
            geometryList.add(geometry);
        }

        // Update the global UBO:
        BufferResource globalUbo = pass.getGlobalUbo();
        ByteBuffer byteBuffer = globalUbo.findData();
        globalUniformValues.writeTo(byteBuffer);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkExtent2D framebufferExtent
                    = chainResources.framebufferExtent(stack);

            int numGeometries = visibleGeometries.size();
            long passHandle = chainResources.passHandle();
            for (int geometryI = 0; geometryI < numGeometries; ++geometryI) {
                Draw draw = pass.getDraw(geometryI);

                BufferResource nonGlobalUbo = draw.getNonGlobalUbo();
                Geometry geometry = geometryList.get(geometryI);
                geometry.writeUniformValuesTo(nonGlobalUbo);

                draw.updateDescriptorSet(geometry, globalUbo);

                long pipelineHandle = createPipeline(pipelineLayoutHandle,
                        framebufferExtent, passHandle, geometry);
                draw.setPipeline(pipelineHandle);
            }
            recordCommandBuffer(imageIndex, pass);

            Frame inFlightFrame = framesInFlight.get(imageIndex);
            if (inFlightFrame != null) {
                inFlightFrame.waitForFence();
            }
            framesInFlight.put(imageIndex, frame);

            CommandSequence sequence = chainResources.getSequence(imageIndex);
            sequence.submitWithSynch(graphicsQueue, frame);

            // Enqueue the image for presentation:
            boolean success = presentImage(imageIndex, frame);
            if (!success) {
                recreateChainResources();
                return;
            }

            currentFrameIndex = (currentFrameIndex + 1) % maxFramesInFlight;
        }
    }

    /**
     * Alter the background color of the window.
     *
     * @param desiredColor the desired color (not null, unaffected,
     * default=black)
     */
    static void setBackgroundColor(Vector4fc desiredColor) {
        backgroundColor.set(desiredColor);
    }

    /**
     * Indicate that the framebuffers need to be resized, along with all the
     * other swap-chain resources.
     */
    static void setNeedsResize() {
        needsResize = true;
    }
    // *************************************************************************
    // private methods

    /**
     * Add debug-messenger information to the specified VkInstanceCreateInfo.
     *
     * @param createInfo the info to modify (not null, modified)
     * @param stack for allocating temporary host buffers (not null)
     */
    private static void addDebugMessengerCreateInfo(
            VkInstanceCreateInfo createInfo, MemoryStack stack) {
        // Create the debug-messenger creation information:
        VkDebugUtilsMessengerCreateInfoEXT messengerCreateInfo
                = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack);
        messengerCreateInfo.sType(
                EXTDebugUtils.VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT
        );

        messengerCreateInfo.messageSeverity(
                EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT
                | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT
                | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT
                | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT);
        messengerCreateInfo.messageType(
                EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT
                | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT);
        messengerCreateInfo.pfnUserCallback(Internals::debugCallback);

        // Save a reference to the Callback, for use during cleanup():
        debugMessengerCallback = messengerCreateInfo.pfnUserCallback();

        long address = messengerCreateInfo.address();
        createInfo.pNext(address);
    }

    /**
     * Select an image format for the depth buffer.
     *
     * @return a supported image format
     */
    private static int chooseDepthBufferFormat() {
        int tiling = VK10.VK_IMAGE_TILING_OPTIMAL;
        int formatFeatures
                = VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
        int result = physicalDevice.findSupportedFormat(tiling, formatFeatures,
                VK10.VK_FORMAT_D32_SFLOAT,
                VK10.VK_FORMAT_D32_SFLOAT_S8_UINT,
                VK10.VK_FORMAT_D24_UNORM_S8_UINT);

        return result;
    }

    /**
     * Configure a shader-stage creation buffer for the specified geometry.
     *
     * @param createInfo the buffer containing 2 structs to configure (not null,
     * modified)
     * @param geometry the geometry to configure for (not null, unaffected)
     * @param stack for allocating temporary host buffers (not null)
     */
    private static void configureShaderStages(
            VkPipelineShaderStageCreateInfo.Buffer createInfo,
            Geometry geometry, MemoryStack stack) {
        ByteBuffer entryPoint = stack.UTF8("main");

        // [0] vertex shader:
        VkPipelineShaderStageCreateInfo vertCreateInfo = createInfo.get(0);
        vertCreateInfo.sType(
                VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

        ShaderProgram shaderProgram = geometry.getProgram();
        long vertModuleHandle = shaderProgram.vertModuleHandle();
        vertCreateInfo.module(vertModuleHandle);
        vertCreateInfo.pName(entryPoint);
        vertCreateInfo.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT);

        // [1] - fragment shader:
        VkPipelineShaderStageCreateInfo fragCreateInfo = createInfo.get(1);
        fragCreateInfo.sType(
                VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

        long fragModuleHandle = shaderProgram.fragModuleHandle();
        fragCreateInfo.module(fragModuleHandle);
        fragCreateInfo.pName(entryPoint);
        fragCreateInfo.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
    }

    /**
     * Create the main swapchain and all resources that depend on it.
     */
    private static void createChainResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SurfaceSummary surface
                    = physicalDevice.summarizeSurface(surfaceHandle, stack);
            chainResources = new ChainResources(
                    surface, descriptorSetLayoutHandle, framebufferWidth,
                    framebufferHeight, depthBufferFormat, pipelineLayoutHandle);
            framebufferHeight = chainResources.framebufferHeight();
            framebufferWidth = chainResources.framebufferWidth();

            int numImages = chainResources.countImages();
            createSyncObjects(numImages);
        }
    }

    /**
     * Create the logical device for resource creation/destruction.
     */
    private static void createLogicalDevice() {
        logicalDevice = new LogicalDevice(physicalDevice, surfaceHandle);
        vkDevice = logicalDevice.getVkDevice();
        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);

        // Obtain access to the graphics queue:
        int familyIndex = queueFamilies.graphics();
        graphicsQueue = logicalDevice.getQueue(familyIndex);

        // Obtain access to the presentation queue:
        familyIndex = queueFamilies.presentation();
        presentationQueue = logicalDevice.getQueue(familyIndex);
    }

    /**
     * Create a graphics pipeline.
     *
     * @param pipelineLayoutHandle the handle of the graphics-pipeline layout to
     * use (not null)
     * @param framebufferExtent the framebuffer dimensions (not null)
     * @param passHandle the handle of the VkRenderPass to use (not null)
     * @param geometry the Geometry to be rendered (not null)
     * @return the handle of the new {@code VkPipeline} (not null)
     */
    private static long createPipeline(long pipelineLayoutHandle,
            VkExtent2D framebufferExtent, long passHandle, Geometry geometry) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // color-blend attachment state - per attached frame buffer:
            VkPipelineColorBlendAttachmentState.Buffer cbaState
                    = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            cbaState.alphaBlendOp(VK10.VK_BLEND_OP_ADD);
            cbaState.blendEnable(false); // no linear blend
            cbaState.colorWriteMask(
                    VK10.VK_COLOR_COMPONENT_R_BIT
                    | VK10.VK_COLOR_COMPONENT_G_BIT
                    | VK10.VK_COLOR_COMPONENT_B_BIT
                    | VK10.VK_COLOR_COMPONENT_A_BIT);
            cbaState.colorBlendOp(VK10.VK_BLEND_OP_ADD);
            cbaState.dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
            cbaState.dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
            cbaState.srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE);
            cbaState.srcColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE);

            // color-blend state - affects all attached framebuffers:
            //   combine frag shader output color with color in the framebuffer
            VkPipelineColorBlendStateCreateInfo cbsCreateInfo
                    = VkPipelineColorBlendStateCreateInfo.calloc(stack);
            cbsCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO
            );

            cbsCreateInfo.blendConstants(stack.floats(0f, 0f, 0f, 0f));
            cbsCreateInfo.logicOp(VK10.VK_LOGIC_OP_COPY);
            cbsCreateInfo.logicOpEnable(false); // no logic operation blend
            cbsCreateInfo.pAttachments(cbaState);

            // depth/stencil state:
            VkPipelineDepthStencilStateCreateInfo dssCreateInfo
                    = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
            dssCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO
            );

            boolean depthTestEnable = geometry.isDepthTest();
            dssCreateInfo.depthTestEnable(depthTestEnable);

            dssCreateInfo.depthWriteEnable(true);
            dssCreateInfo.depthCompareOp(VK10.VK_COMPARE_OP_LESS);
            dssCreateInfo.depthBoundsTestEnable(false);
            dssCreateInfo.minDepthBounds(0f); // optional
            dssCreateInfo.maxDepthBounds(1f); // optional
            dssCreateInfo.stencilTestEnable(false);

            // input-assembly state indicates:
            //  1. how to assemble vertices into primitives (topology)
            //  2. whether primitive restart should be enabled
            Mesh mesh = geometry.getMesh();
            mesh.makeImmutable();
            VkPipelineInputAssemblyStateCreateInfo iasCreateInfo
                    = mesh.generateIasCreateInfo(stack);

            // multisample state:
            VkPipelineMultisampleStateCreateInfo msCreateInfo
                    = VkPipelineMultisampleStateCreateInfo.calloc(stack);
            msCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO
            );

            msCreateInfo.alphaToCoverageEnable(false);
            msCreateInfo.alphaToOneEnable(false);
            msCreateInfo.minSampleShading(1f);
            msCreateInfo.pSampleMask(0);
            msCreateInfo.rasterizationSamples(numMsaaSamples);
            msCreateInfo.sampleShadingEnable(false);

            // rasterization state:
            //    1. turns mesh primitives into fragments
            //    2. performs depth testing, face culling, and the scissor test
            //    3. fills polygons and/or edges (wireframe)
            //    4. determines front/back faces of polygons (winding)
            VkPipelineRasterizationStateCreateInfo rsCreateInfo
                    = geometry.generateRasterizationState(stack);

            VkPipelineShaderStageCreateInfo.Buffer stageCreateInfos
                    = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            configureShaderStages(stageCreateInfos, geometry, stack);

            // vertex-input state - describes format of vertex data:
            VkPipelineVertexInputStateCreateInfo visCreateInfo
                    = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            visCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
            );

            ShaderProgram shaderProgram = geometry.getProgram();
            VkVertexInputAttributeDescription.Buffer pAttributeDesc
                    = shaderProgram.generateAttributeDescriptions(stack);
            visCreateInfo.pVertexAttributeDescriptions(pAttributeDesc);

            VkVertexInputBindingDescription.Buffer pBindingDesc
                    = shaderProgram.generateBindingDescription(stack);
            visCreateInfo.pVertexBindingDescriptions(pBindingDesc);

            // viewport location and dimensions:
            int height = framebufferExtent.height();
            int width = framebufferExtent.width();
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.height(height);
            viewport.maxDepth(1f);
            viewport.minDepth(0f);
            viewport.width(width);
            viewport.x(0f);
            viewport.y(0f);

            // scissor - region of the frame buffer where pixels are written:
            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.offset(VkOffset2D.calloc(stack).set(0, 0));
            scissor.extent(framebufferExtent);

            // viewport state:
            VkPipelineViewportStateCreateInfo vsCreateInfo
                    = VkPipelineViewportStateCreateInfo.calloc(stack);
            vsCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO);

            vsCreateInfo.pViewports(viewport);
            vsCreateInfo.pScissors(scissor);

            // Create the pipeline:
            VkGraphicsPipelineCreateInfo.Buffer pCreateInfo
                    = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO);

            pCreateInfo.basePipelineHandle(VK10.VK_NULL_HANDLE);
            pCreateInfo.basePipelineIndex(-1);
            pCreateInfo.layout(pipelineLayoutHandle);
            pCreateInfo.pColorBlendState(cbsCreateInfo);
            pCreateInfo.pDepthStencilState(dssCreateInfo);
            pCreateInfo.pInputAssemblyState(iasCreateInfo);
            pCreateInfo.pMultisampleState(msCreateInfo);
            pCreateInfo.pRasterizationState(rsCreateInfo);
            pCreateInfo.pStages(stageCreateInfos);
            pCreateInfo.pVertexInputState(visCreateInfo);
            pCreateInfo.pViewportState(vsCreateInfo);
            pCreateInfo.renderPass(passHandle);
            pCreateInfo.subpass(0);

            long pipelineCache = VK10.VK_NULL_HANDLE; // disable cacheing
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateGraphicsPipelines(vkDevice,
                    pipelineCache, pCreateInfo, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create graphics pipeline");
            long result = pHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }

    /**
     * Create a display surface in the application's window.
     *
     * @return the handle of the new {@code VkSurfaceKHR} (not null)
     */
    private static long createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long windowHandle = BaseApplication.glfwWindowHandle();
            LongBuffer pSurfaceHandle = stack.mallocLong(1);
            int retCode = GLFWVulkan.glfwCreateWindowSurface(
                    vkInstance, windowHandle, defaultAllocator, pSurfaceHandle);
            Utils.checkForError(retCode, "create window surface");
            long result = pSurfaceHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }

    /**
     * Create synchronization objects for each image in the swapchain.
     *
     * @param numImages the number of images in the swap chain
     */
    private static void createSyncObjects(int numImages) {
        inFlightFrames = new Frame[maxFramesInFlight];
        framesInFlight = new HashMap<>(numImages);

        for (int i = 0; i < maxFramesInFlight; ++i) {
            inFlightFrames[i] = new Frame();
        }
    }

    /**
     * Create a Vulkan instance to provide the application with access to the
     * Vulkan API.
     *
     * @param appName the name of the application (may be null)
     * @param appVersion the version numbers of the application
     */
    private static void createVkInstance(String appName, int appVersion) {
        // Verify that all required validation layers are available:
        Set<String> availableSet = listAvailableLayers();
        for (String layerName : requiredLayers) {
            if (!availableSet.contains(layerName)) {
                throw new RuntimeException(
                        "Unavailable validation layer: " + layerName);
            }
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create the application info struct:
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack);
            appInfo.sType(VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO);

            appInfo.apiVersion(VK10.VK_API_VERSION_1_0); // Vulkan v1.0

            appInfo.pApplicationName(stack.UTF8Safe(appName));
            appInfo.applicationVersion(appVersion);

            appInfo.pEngineName(stack.UTF8Safe(BaseApplication.engineName));
            appInfo.engineVersion(BaseApplication.engineVersion);

            // Create the instance-creation information:
            VkInstanceCreateInfo createInfo
                    = VkInstanceCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO);
            createInfo.pApplicationInfo(appInfo);

            // Specify the required extensions:
            PointerBuffer extensionNames
                    = listRequiredInstanceExtensions(stack);
            createInfo.ppEnabledExtensionNames(extensionNames);

            if (enableDebugging) {
                // Specify the required validation layers:
                PointerBuffer layerNames = listRequiredLayers(stack);
                createInfo.ppEnabledLayerNames(layerNames);

                // Configure a debug messenger:
                addDebugMessengerCreateInfo(createInfo, stack);
            }

            // Allocate a buffer to receive the pointer to the new instance:
            PointerBuffer pPointer = stack.mallocPointer(1);

            int retCode = VK10.vkCreateInstance(
                    createInfo, defaultAllocator, pPointer);
            Utils.checkForError(retCode, "create Vulkan instance");
            long handle = pPointer.get(0);
            vkInstance = new VkInstance(handle, createInfo);
        }
    }

    /**
     * Handle a single debug callback. The callback must not invoke any Vulkan
     * APIs.
     *
     * @param severity the severity of the event that triggered the callback
     * @param messageType the event type(s) that triggered the callback
     * @param pCallbackData the address of any callback-specific data
     * @param pUserData the address of any user data provided when the
     * {@code VkDebugUtilsMessengerEXT} was created
     * @return {@code VK_FALSE}
     */
    private static int debugCallback(int severity, int messageType,
            long pCallbackData, long pUserData) {
        PrintStream stream;
        int warning
                = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
        if (severity > warning) {
            stream = System.err; // warnings and severe error messages
        } else {
            //stream = System.out; // verbose or info-level diagnostic messages
            return VK10.VK_FALSE;
        }

        VkDebugUtilsMessengerCallbackDataEXT callbackData
                = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
        String message = callbackData.pMessageString();
        stream.println("[debugCallback] " + message);
        stream.flush();
        /*
         * The {@code VK_TRUE} return value is reserved
         * for use in layer development.
         */
        return VK10.VK_FALSE;
    }

    /**
     * Destroy the main swapchain and any resources that depend on it or the
     * extent of its frame buffers.
     */
    private static void destroyChainResources() {
        if (inFlightFrames != null) {
            for (Frame frame : inFlightFrames) {
                frame.destroy();
            }
            inFlightFrames = null;
        }

        if (chainResources != null) {
            chainResources.destroy();
            chainResources = null;
        }
    }

    /**
     * Enumerate all available instance validation layers.
     *
     * @return a new set of layer names (not null)
     */
    private static Set<String> listAvailableLayers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the available instance validation layers:
            IntBuffer pCount = stack.mallocInt(1);
            int retCode
                    = VK10.vkEnumerateInstanceLayerProperties(pCount, null);
            Utils.checkForError(retCode, "count layer properties");
            int numLayers = pCount.get(0);

            // Enumerate the available instance validation layers:
            VkLayerProperties.Buffer buffer
                    = VkLayerProperties.malloc(numLayers, stack);
            retCode = VK10.vkEnumerateInstanceLayerProperties(pCount, buffer);
            Utils.checkForError(retCode, "enumerate layer properties");
            Iterator<VkLayerProperties> iterator = buffer.iterator();

            Set<String> result = new TreeSet<>();
            while (iterator.hasNext()) {
                VkLayerProperties properties = iterator.next();
                String layerName = properties.layerNameString();
                result.add(layerName);
            }

            return result;
        }
    }

    /**
     * Enumerate all required instance extensions.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a temporary buffer containing the names of all instance
     * extensions required by the application
     */
    private static PointerBuffer
            listRequiredInstanceExtensions(MemoryStack stack) {
        PointerBuffer result
                = GLFWVulkan.glfwGetRequiredInstanceExtensions();

        if (enableDebugging) {
            // Vulkan debug utils require one additional instance extension:
            result = Utils.appendStringPointer(result,
                    EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME, stack);
        }

        // Add an extension used to deal with portability subsets:
        result = Utils.appendStringPointer(result,
                KHRGetPhysicalDeviceProperties2.VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME,
                stack);

        return result;
    }

    /**
     * Return the next presentation image in the swapchain.
     *
     * @param frame (not null)
     * @return the index of the image to present (&ge;0) or null if the
     * swapchain should be recreated
     */
    private static Integer nextPresentationImage(Frame frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long chainHandle = chainResources.chainHandle();
            long imageAvailableSemaphoreHandle
                    = frame.imageAvailableSemaphoreHandle();
            long fenceToSignalHandle = VK10.VK_NULL_HANDLE;
            IntBuffer pImageIndex = stack.mallocInt(1);
            int retCode = KHRSwapchain.vkAcquireNextImageKHR(vkDevice,
                    chainHandle, noTimeout, imageAvailableSemaphoreHandle,
                    fenceToSignalHandle, pImageIndex);

            // check for abnormal return codes:
            boolean outdated
                    = (retCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR);
            if (outdated && BaseApplication.isDebuggingEnabled()) {
                System.out.println("vkAcquireNextImageKHR"
                        + " reports the swapchain is outdated.");
            }

            boolean suboptimal = (retCode == KHRSwapchain.VK_SUBOPTIMAL_KHR);
            if (suboptimal && BaseApplication.isDebuggingEnabled()) {
                System.out.println("vkAcquireNextImageKHR"
                        + " reports the swapchain is suboptimal.");
            }

            Integer result;
            if (outdated || suboptimal) {
                result = null;
            } else {
                Utils.checkForError(
                        retCode, "acquire the next presentation image");
                result = pImageIndex.get(0);
                assert result >= 0 : result;
                assert result < chainResources.countImages() : result;
            }

            return result;
        }
    }

    /**
     * Enqueue an image for presentation.
     *
     * @param imageIndex the index of the image to present (&ge;0)
     * @param frame (not null)
     * @return true if successful, or false if the swapchain should be recreated
     */
    private static boolean presentImage(int imageIndex, Frame frame) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPresentInfoKHR presentationInfo = VkPresentInfoKHR.calloc(stack);
            presentationInfo.sType(
                    KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

            IntBuffer pImageIndices = stack.ints(imageIndex);
            presentationInfo.pImageIndices(pImageIndices);

            long chainHandle = chainResources.chainHandle();
            LongBuffer pSwapchains = stack.longs(chainHandle);
            presentationInfo.pSwapchains(pSwapchains);
            presentationInfo.swapchainCount(pSwapchains.capacity());

            long waitSemaphore = frame.renderFinishedSemaphoreHandle();
            LongBuffer pWaitSemaphores = stack.longs(waitSemaphore);
            presentationInfo.pWaitSemaphores(pWaitSemaphores);

            int retCode = KHRSwapchain.vkQueuePresentKHR(
                    presentationQueue, presentationInfo);

            // check for abnormal return codes:
            boolean outdated
                    = (retCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR);
            if (outdated && BaseApplication.isDebuggingEnabled()) {
                System.out.println(
                        "vkQueuePresentKHR reports the swapchain is outdated.");
            }

            boolean suboptimal = (retCode == KHRSwapchain.VK_SUBOPTIMAL_KHR);
            if (suboptimal && BaseApplication.isDebuggingEnabled()) {
                System.out.println("vkQueuePresentKHR"
                        + " reports the swapchain is suboptimal.");
            }

            boolean success;
            if (outdated || suboptimal || needsResize) {
                success = false;
            } else {
                Utils.checkForError(retCode, "enqueue image for presentation");
                success = true;
            }

            return success;
        }
    }

    /**
     * Record a command sequence to render the specified presentation image
     * using a single pass.
     *
     * @param imageIndex the index of the presentation image in the swapchain
     * (&ge;0)
     * @param pass the render-pass resources to use (not null)
     */
    private static void recordCommandBuffer(int imageIndex, Pass pass) {
        CommandSequence sequence = chainResources.getSequence(imageIndex);
        sequence.reset(); // not a one-time sequence
        sequence.addBeginRenderPass(chainResources, pass);

        List<Geometry> geometryList = pass.getGeometryList();
        int numGeometries = geometryList.size();

        for (int geometryI = 0; geometryI < numGeometries; ++geometryI) {
            Draw draw = pass.getDraw(geometryI);
            sequence.addBindPipeline(draw);

            Geometry geometry = geometryList.get(geometryI);
            sequence.addBindVertexBuffers(geometry);

            Mesh mesh = geometry.getMesh();
            if (mesh.isIndexed()) {
                IndexBuffer indexBuffer = mesh.getIndexBuffer();
                sequence.addBindIndexBuffer(indexBuffer);
            }

            sequence.addBindDescriptors(draw, pipelineLayoutHandle);
            sequence.addDraw(geometry);
        }

        sequence.addEndRenderPass();
        sequence.end();
    }

    /**
     * Re-create the main swapchain and any resources that depend on it or the
     * extents of its frame buffers.
     */
    private static void recreateChainResources() {
        // If the window is minimized, wait for it to return to the foreground.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long windowHandle = BaseApplication.glfwWindowHandle();
            IntBuffer pWidth = stack.ints(0);
            IntBuffer pHeight = stack.ints(0);
            GLFW.glfwGetFramebufferSize(windowHandle, pWidth, pHeight);
            if (pWidth.get(0) == 0 && pHeight.get(0) == 0) {
                if (BaseApplication.isDebuggingEnabled()) {
                    System.out.println("The window is minimized.");
                }
                while (pWidth.get(0) == 0 && pHeight.get(0) == 0) {
                    GLFW.glfwWaitEvents();
                    GLFW.glfwGetFramebufferSize(windowHandle, pWidth, pHeight);
                }
                if (BaseApplication.isDebuggingEnabled()) {
                    System.out.println("The window returns to the foreground.");
                }
            }
        }

        if (logicalDevice != null) {
            logicalDevice.waitIdle();
        }

        if (BaseApplication.isDebuggingEnabled()) {
            System.out.println("Recreate the swap chain.");
        }
        destroyChainResources();
        needsResize = false;
        createChainResources();
    }

    /**
     * Select the physical device best suited to displaying the surface.
     *
     * @param app the application instance (not null)
     */
    private static void selectPhysicalDevice(BaseApplication app) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the available devices:
            IntBuffer pCount = stack.mallocInt(1);
            int retCode
                    = VK10.vkEnumeratePhysicalDevices(vkInstance, pCount, null);
            Utils.checkForError(retCode, "count physical devices");
            int numDevices = pCount.get(0);
            if (numDevices == 0) {
                throw new RuntimeException(
                        "Didn't find a physical device with Vulkan support");
            }
            System.out.println("numDevices = " + numDevices);

            PointerBuffer pPointers = stack.mallocPointer(numDevices);
            retCode = VK10.vkEnumeratePhysicalDevices(
                    vkInstance, pCount, pPointers);
            Utils.checkForError(retCode, "enumerate physical devices");

            // Select the most suitable device:
            physicalDevice = null;
            float bestScore = Float.NEGATIVE_INFINITY;
            for (int deviceIndex = 0; deviceIndex < numDevices; ++deviceIndex) {
                long handle = pPointers.get(deviceIndex);
                PhysicalDevice pd = new PhysicalDevice(handle, vkInstance);
                boolean diagnose = false;
                float score = pd.suitability(surfaceHandle, diagnose);
                if (score > bestScore) {
                    bestScore = score;
                    physicalDevice = pd;
                }
            }

            if (bestScore <= 0f) {
                System.out.println(
                        "Failed to find a suitable Vulkan device, numDevices = "
                        + numDevices + ", bestScore = " + bestScore);
                for (int deviceI = 0; deviceI < numDevices; ++deviceI) {
                    long handle = pPointers.get(deviceI);
                    PhysicalDevice pd = new PhysicalDevice(handle, vkInstance);
                    boolean diagnose = true;
                    float score = pd.suitability(surfaceHandle, diagnose);
                    System.out.printf("    suitability score = %s%n", score);
                }
                System.out.flush();
                app.cleanUpBase();
                System.exit(0);
            }
        }
    }
}
