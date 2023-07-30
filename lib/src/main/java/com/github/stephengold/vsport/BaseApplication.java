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
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import jme3utilities.Validate;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.Callback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkClearDepthStencilValue;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkComponentMapping;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

/**
 * A single-window, 3-D visualization application using LWJGL and Vulkan.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
public abstract class BaseApplication {
    // *************************************************************************
    // constants

    /**
     * true to enable debugging output and optional runtime checks, or false to
     * disable them
     */
    final private static boolean enableDebugging = false;
    /**
     * version of the V-Sport graphics engine
     */
    final private static int engineVersion = VK10.VK_MAKE_VERSION(0, 1, 0);
    /**
     * maximum number of frames in flight
     */
    final private static int maxFramesInFlight = 2;
    /**
     * timeout period for synchronization (in nanoseconds)
     */
    final public static long noTimeout = 0xFFFFFFFFFFFFFFFFL;
    /**
     * name of the graphics engine
     */
    final private static String engineName = "V-Sport";
    /**
     * use the default allocator for direct buffers
     */
    final private static VkAllocationCallbacks defaultAllocator = null;
    // *************************************************************************
    // fields

    /**
     * true if the frame buffer needs to be resized
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
     * synchronization objects for frames in flight
     */
    private static Frame[] inFlightFrames;
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
    private static int frameBufferHeight = 600;
    /**
     * width of the displayed frame buffer (in pixels)
     */
    private static int frameBufferWidth = 800;
    /**
     * command buffers (at least one per image in the chain)
     */
    final private static List<VkCommandBuffer> commandBuffers
            = new ArrayList<>(4);
    /**
     * handle of the command pool for the main window
     */
    private static long commandPoolHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the descriptor-set layout for the UBO
     */
    private static long descriptorSetLayoutHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the graphics-pipeline layout
     */
    private static long pipelineLayoutHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the VkSampler for textures
     */
    private static long samplerHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the surface for the main window
     */
    private static long surfaceHandle = VK10.VK_NULL_HANDLE;
    /**
     * GLFW handle of the window used to render geometries
     */
    private static long windowHandle = VK10.VK_NULL_HANDLE;
    /**
     * map indices to frames
     */
    private static Map<Integer, Frame> framesInFlight;
    /**
     * map program names to programs
     */
    final private static Map<String, ShaderProgram> programMap
            = new HashMap<>(16);
    /**
     * mesh of triangles to be rendered
     */
    private static Mesh sampleMesh;
    /**
     * physical device to display the main window
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
     * shader modules for rendering
     */
    private static ShaderProgram shaderProgram;
    /**
     * sample texture for texture mapping
     */
    private static Texture sampleTexture;
    /**
     * values to be written to the uniform buffer object
     */
    final private static UniformValues uniformValues = new UniformValues();
    /**
     * logical device to display the main window
     */
    private static VkDevice logicalDevice;
    /**
     * link this application to the lwjgl-vulkan library
     */
    private static VkInstance vkInstance;
    /**
     * graphics queue for the main window
     */
    private static VkQueue graphicsQueue;
    /**
     * presentation queue for the main window
     */
    private static VkQueue presentationQueue;
    // *************************************************************************
    // new methods exposed

    /**
     * Return the allocator for direct buffers.
     *
     * @return the pre-existing instance, or null to use the default allocator
     */
    static VkAllocationCallbacks allocator() {
        return defaultAllocator;
    }

    /**
     * Convert the specified image from one layout to another.
     *
     * @param imageHandle the handle of the image to convert
     * @param format the image format
     * @param oldLayout the pre-existing layout
     * @param newLayout the desired layout
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     */
    static void alterImageLayout(long imageHandle, int format, int oldLayout,
            int newLayout, int numMipLevels) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer pBarrier
                    = VkImageMemoryBarrier.calloc(1, stack);
            pBarrier.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER);

            pBarrier.dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);
            pBarrier.image(imageHandle);
            pBarrier.newLayout(newLayout);
            pBarrier.oldLayout(oldLayout);
            pBarrier.srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED);

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
                pBarrier.dstAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
                pBarrier.srcAccessMask(VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
                pBarrier.dstAccessMask(VK10.VK_ACCESS_SHADER_READ_BIT);

                sourceStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
                destinationStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;

            } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    && newLayout
                    == VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL) {
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
                pBarrier.dstAccessMask(
                        VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                        | VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
                pBarrier.srcAccessMask(0x0);

                sourceStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
                destinationStage
                        = VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;

            } else {
                throw new IllegalArgumentException(
                        "Unsupported transition, oldLayout=" + oldLayout);
            }

            Commands commandBuffer = new Commands();
            commandBuffer.addBarrier(sourceStage, destinationStage, pBarrier);
            commandBuffer.submitToGraphicsQueue();
        }
    }

    /**
     * Return the aspect ratio of the displayed frame buffer.
     *
     * @return the width divided by the height (&gt;0)
     */
    public static float aspectRatio() {
        float ratio = frameBufferWidth / (float) frameBufferHeight;

        assert ratio > 0f : ratio;
        return ratio;
    }

    /**
     * Cleanly terminate the application after the main window closes for any
     * reason.
     */
    static void cleanup() {
        if (logicalDevice != null) {
            // Await completion of all GPU operations:
            VK10.vkDeviceWaitIdle(logicalDevice);
        }
        /*
         * Destroy resources in the reverse of the order they were created,
         * starting with the chain resources:
         */
        destroyChainResources();
        if (shaderProgram != null) {
            shaderProgram.destroy();
            shaderProgram = null;
        }

        // Destroy the pipeline layout:
        if (pipelineLayoutHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipelineLayout(
                    logicalDevice, pipelineLayoutHandle, defaultAllocator);
            pipelineLayoutHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the descriptor-set layout that's used to configure pipelines:
        if (descriptorSetLayoutHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorSetLayout(
                    logicalDevice, descriptorSetLayoutHandle, defaultAllocator);
            descriptorSetLayoutHandle = VK10.VK_NULL_HANDLE;
        }

        if (sampleMesh != null) {
            sampleMesh.destroy();
        }

        // Destroy the texture sampler:
        if (samplerHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroySampler(
                    logicalDevice, samplerHandle, defaultAllocator);
            samplerHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the sample texture:
        if (sampleTexture != null) {
            sampleTexture.destroy();
            sampleTexture = null;
        }

        // Destroy the command pool and its buffers:
        commandBuffers.clear();
        if (commandPoolHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyCommandPool(
                    logicalDevice, commandPoolHandle, defaultAllocator);
            commandPoolHandle = VK10.VK_NULL_HANDLE;
        }

        // Destroy the logical device:
        if (logicalDevice != null) {
            VK10.vkDestroyDevice(logicalDevice, defaultAllocator);
            logicalDevice = null;
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

        cleanupGlfw();
    }

    /**
     * Return the handle of the main command pool.
     *
     * @return the handle (not null)
     */
    static long commandPoolHandle() {
        assert commandPoolHandle != VK10.VK_NULL_HANDLE;
        return commandPoolHandle;
    }

    /**
     * Copy the contents of one buffer object to another.
     *
     * @param sourceHandle the handle of the source buffer object
     * @param destHandle the handle of the destination buffer object
     * @param numBytes the number of bytes to copy
     */
    static void copyBuffer(long sourceHandle, long destHandle, long numBytes) {
        Commands commands = new Commands();
        commands.addCopyBufferToBuffer(numBytes, sourceHandle, destHandle);
        commands.submitToGraphicsQueue();
    }

    /**
     * Copy the data from the specified buffer to the specified 2-D image.
     *
     * @param bufferHandle the handle of the source buffer
     * @param imageHandle the handle of the destination image
     * @param width the width of the image (in pixels)
     * @param height the height of the image (in pixels)
     */
    static void copyBufferToImage(
            long bufferHandle, long imageHandle, int width, int height) {
        Commands commands = new Commands();
        commands.addCopyBufferToImage(
                bufferHandle, imageHandle, width, height);
        commands.submitToGraphicsQueue();
    }

    /**
     * Create a buffer object with some memory bound to it.
     *
     * @param numBytes the desired buffer capacity (in bytes)
     * @param usage a bitmask
     * @param requiredProperties a bitmask
     * @param pBuffer to store the handle of the resulting buffer object (not
     * null, modified)
     * @param pMemory to store the handle of the buffer's memory (not null,
     * modified)
     */
    static void createBuffer(long numBytes, int usage,
            int requiredProperties, LongBuffer pBuffer, LongBuffer pMemory) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create the buffer object:
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO);

            createInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            createInfo.size(numBytes);
            createInfo.usage(usage);

            int retCode = VK10.vkCreateBuffer(
                    logicalDevice, createInfo, defaultAllocator, pBuffer);
            Utils.checkForError(retCode, "create buffer object");
            long bufferHandle = pBuffer.get(0);

            // Query the buffer's memory requirements:
            VkMemoryRequirements memRequirements
                    = VkMemoryRequirements.malloc(stack);
            VK10.vkGetBufferMemoryRequirements(
                    logicalDevice, bufferHandle, memRequirements);

            // Allocate memory for the buffer:
            // TODO a custom allocator to reduce the number of allocations
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);

            long allocationBytes = memRequirements.size();
            allocInfo.allocationSize(allocationBytes);

            int typeFilter = memRequirements.memoryTypeBits();
            int memoryTypeIndex = physicalDevice.findMemoryType(
                    typeFilter, requiredProperties);
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            retCode = VK10.vkAllocateMemory(
                    logicalDevice, allocInfo, defaultAllocator, pMemory);
            Utils.checkForError(retCode, "allocate memory for a buffer");
            long memoryHandle = pMemory.get(0);

            // Bind the newly allocated memory to the buffer object:
            int offset = 0;
            retCode = VK10.vkBindBufferMemory(
                    logicalDevice, bufferHandle, memoryHandle, offset);
            Utils.checkForError(retCode, "bind memory to a buffer object");
        }
    }

    /**
     * Create a 2-D image with the specified properties.
     *
     * @param width the desired width (in pixels)
     * @param height the desired height (in pixels)
     * @param numMipLevels the desired number of mip levels (including the
     * original image, &ge;1, &le;31)
     * @param format the desired format
     * @param tiling the desired tiling
     * @param usage a bitmask
     * @param requiredProperties a bitmask
     * @param pImage to store the handle of the resulting image (not null,
     * modified)
     * @param pMemory to store the handle of the image's memory (not null,
     * modified)
     */
    static void createImage(int width, int height, int numMipLevels, int format,
            int tiling, int usage, int requiredProperties,
            LongBuffer pImage, LongBuffer pMemory) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack);
            imageInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO);

            imageInfo.arrayLayers(1);
            imageInfo.extent().depth(1);
            imageInfo.extent().height(height);
            imageInfo.extent().width(width);
            imageInfo.format(format);
            imageInfo.imageType(VK10.VK_IMAGE_TYPE_2D);
            imageInfo.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.mipLevels(numMipLevels);
            imageInfo.samples(VK10.VK_SAMPLE_COUNT_1_BIT);
            imageInfo.sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            imageInfo.tiling(tiling);
            imageInfo.usage(usage);

            int retCode = VK10.vkCreateImage(
                    logicalDevice, imageInfo, defaultAllocator, pImage);
            Utils.checkForError(retCode, "create image");
            long imageHandle = pImage.get(0);

            // Query the images's memory requirements:
            VkMemoryRequirements memRequirements
                    = VkMemoryRequirements.malloc(stack);
            VK10.vkGetImageMemoryRequirements(
                    logicalDevice, imageHandle, memRequirements);

            // Allocate memory for the image:
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack);
            allocInfo.sType(VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO);

            allocInfo.allocationSize(memRequirements.size());
            int memoryTypeIndex = physicalDevice.findMemoryType(
                    memRequirements.memoryTypeBits(), requiredProperties);
            allocInfo.memoryTypeIndex(memoryTypeIndex);

            retCode = VK10.vkAllocateMemory(
                    logicalDevice, allocInfo, defaultAllocator, pMemory);
            Utils.checkForError(retCode, "allocate image memory");
            long memoryHandle = pMemory.get(0);

            // Bind the newly allocated memory to the image object:
            int offset = 0;
            VK10.vkBindImageMemory(
                    logicalDevice, imageHandle, memoryHandle, offset);
        }
    }

    /**
     * Create an image view for the specified image.
     *
     * @param imageHandle the handle of the image
     * @param format the desired format for the view
     * @param aspectMask a bitmask of VK_IMAGE_ASPECT_... values
     * @param numMipLevels the desired number of MIP levels (including the
     * original image, &ge;1, &le;31)
     * @return the handle of the new VkImageView
     */
    static long createImageView(
            long imageHandle, int format, int aspectMask, int numMipLevels) {
        Validate.inRange(numMipLevels, "number of MIP levels", 1, 31);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo createInfo
                    = VkImageViewCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO);

            createInfo.format(format);
            createInfo.image(imageHandle);
            createInfo.viewType(VK10.VK_IMAGE_VIEW_TYPE_2D);

            // Don't swizzle the color channels:
            VkComponentMapping swizzle = createInfo.components();
            swizzle.r(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.g(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.b(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            swizzle.a(VK10.VK_COMPONENT_SWIZZLE_IDENTITY);
            /*
             * The image will be used as a single-layer color target
             * without any MIP mapping.
             */
            VkImageSubresourceRange range = createInfo.subresourceRange();
            range.aspectMask(aspectMask);
            range.baseArrayLayer(0);
            range.baseMipLevel(0);
            range.layerCount(1);
            range.levelCount(numMipLevels);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateImageView(
                    logicalDevice, createInfo, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create image view");
            long result = pHandle.get(0);

            return result;
        }
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
     * Return the physical device for rendering.
     *
     * @return the pre-existing instance (not null)
     */
    static PhysicalDevice getPhysicalDevice() {
        assert physicalDevice != null;
        return physicalDevice;
    }

    /**
     * Return the named ShaderProgram.
     *
     * @param name (not null)
     * @return a valid program (not null)
     */
    static ShaderProgram getProgram(String name) {
        if (!programMap.containsKey(name)) {
            ShaderProgram program = new ShaderProgram(name);
            programMap.put(name, program);
        }

        ShaderProgram result = programMap.get(name);
        assert result != null;
        return result;
    }

    /**
     * Test whether debugging is enabled.
     *
     * @return true if enabled, otherwise false
     */
    public static boolean isDebuggingEnabled() {
        return enableDebugging;
    }

    /**
     * Enumerate all device extensions required by this application.
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
     * @param stack for memory allocation (not null)
     * @return the names of the validation layers required by this application
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
     * Return the logical device for rendering.
     *
     * @return the pre-existing instance (not null)
     */
    static VkDevice logicalDevice() {
        assert logicalDevice != null;
        return logicalDevice;
    }

    /**
     * Alter the title of the main window.
     *
     * @param text the desired text (in UTF-8 encoding)
     */
    public static void setWindowTitle(CharSequence text) {
        GLFW.glfwSetWindowTitle(windowHandle, text);
    }

    /**
     * Start the application (simplified interface).
     *
     * @param appName the name of the application
     */
    public static void start(String appName) {
        start(appName, 0, 0, 0);
    }

    /**
     * Start the application.
     *
     * @param appName the name of the application (not null)
     * @param appMajor the major version number of the application
     * @param appMinor the minor version number of the application
     * @param appPatch the patch version number of the application
     */
    public static void start(
            String appName, int appMajor, int appMinor, int appPatch) {
        // Generate the initial text for the window's title bar:
        String title;
        if (appMajor == 0 && appMinor == 0 && appPatch == 0) {
            title = appName;
        } else {
            title = String.format(
                    "%s v%d.%d.%d", appName, appMajor, appMinor, appPatch);
        }

        try {
            initializeGlfw(title);

            requiredDeviceExtensions
                    .add(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            if (enableDebugging) {
                requiredLayers.add("VK_LAYER_KHRONOS_validation");
            }

            int appVersion = VK10.VK_MAKE_VERSION(appMajor, appMinor, appPatch);
            initializeVulkan(appName, appVersion);

            mainUpdateLoop();

        } catch (Exception exception) {
            System.err.print("Caught ");
            exception.printStackTrace();
            System.err.flush();

        } finally {
            cleanup();
        }
    }
    // *************************************************************************
    // private methods

    /**
     * Allocate command buffers as needed.
     *
     * @param numBuffersNeeded the number of command buffers needed
     * @param addBuffers storage for allocated command buffers (not null, added
     * to)
     */
    private static void addCommandBuffers(
            int numBuffersNeeded, List<VkCommandBuffer> addBuffers) {
        numBuffersNeeded -= addBuffers.size();
        if (numBuffersNeeded <= 0) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create a block of command buffers:
            PointerBuffer pPointers = stack.mallocPointer(numBuffersNeeded);
            VkCommandBufferAllocateInfo allocInfo
                    = VkCommandBufferAllocateInfo.calloc(stack);
            allocInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);

            allocInfo.commandBufferCount(numBuffersNeeded);
            allocInfo.commandPool(commandPoolHandle);
            allocInfo.level(VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY);

            int retCode = VK10.vkAllocateCommandBuffers(
                    logicalDevice, allocInfo, pPointers);
            Utils.checkForError(retCode, "allocate command buffers");

            // Collect the command buffers in a list:
            for (int i = 0; i < numBuffersNeeded; ++i) {
                long pointer = pPointers.get(i);
                VkCommandBuffer commandBuffer
                        = new VkCommandBuffer(pointer, logicalDevice);
                addBuffers.add(commandBuffer);
            }
        }
    }

    /**
     * Add debug-messenger information to the specified VkInstanceCreateInfo.
     *
     * @param createInfo the info to modify (not null, modified)
     * @param stack for memory allocation (not null)
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
        messengerCreateInfo.pfnUserCallback(BaseApplication::debugCallback);

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
     * Destroy the main window and terminate GLFW.
     */
    private static void cleanupGlfw() {
        if (windowHandle != MemoryUtil.NULL) {
            GLFWFramebufferSizeCallback resizeCallback
                    = GLFW.glfwSetFramebufferSizeCallback(windowHandle, null);
            if (resizeCallback != null) {
                resizeCallback.free();
            }

            GLFW.glfwDestroyWindow(windowHandle);
            windowHandle = MemoryUtil.NULL;
        }

        // Cancel the error callback:
        GLFWErrorCallback errorCallback = GLFW.glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }

        GLFW.glfwTerminate();
    }

    /**
     * Create the main swapchain and all resources that depend on it.
     */
    private static void createChainResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SurfaceSummary surface
                    = physicalDevice.summarizeSurface(surfaceHandle, stack);
            chainResources = new ChainResources(
                    surface, descriptorSetLayoutHandle,
                    physicalDevice, frameBufferWidth, frameBufferHeight,
                    depthBufferFormat, samplerHandle,
                    pipelineLayoutHandle, sampleMesh,
                    shaderProgram, sampleTexture);
            frameBufferHeight = chainResources.framebufferHeight();
            frameBufferWidth = chainResources.framebufferWidth();
        }

        int numImages = chainResources.countImages();
        createSyncObjects(numImages);

        addCommandBuffers(numImages, commandBuffers);
        recordCommandBuffers(numImages);
    }

    /**
     * Create an (empty) command-buffer pool for the main window.
     */
    private static void createCommandPool() {
        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);
        int familyIndex = queueFamilies.graphics();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo
                    = VkCommandPoolCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);

            createInfo.flags(
                    VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            createInfo.queueFamilyIndex(familyIndex);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateCommandPool(
                    logicalDevice, createInfo, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create command-buffer pool");
            commandPoolHandle = pHandle.get(0);
        }
    }

    /**
     * Create the descriptor-set layout.
     */
    private static void createDescriptorSetLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer pBindings
                    = VkDescriptorSetLayoutBinding.calloc(2, stack);

            // Define the bindings for the first descriptor set (the UBO):
            VkDescriptorSetLayoutBinding uboBinding = pBindings.get(0);
            uboBinding.binding(0);
            uboBinding.descriptorCount(1); // a single UBO
            uboBinding.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uboBinding.pImmutableSamplers(null);

            // The UBOs will be used only by the vertex-shader stage:
            uboBinding.stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT);

            VkDescriptorSetLayoutBinding samplerBinding = pBindings.get(1);
            samplerBinding.binding(1);
            samplerBinding.descriptorCount(1); // a single sampler
            samplerBinding.descriptorType(
                    VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            samplerBinding.pImmutableSamplers(null);

            // The sampler will be used only by the fragment-shader stage:
            samplerBinding.stageFlags(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

            // Create the descriptor-set layout:
            LongBuffer pHandle = stack.mallocLong(1);
            VkDescriptorSetLayoutCreateInfo createInfo
                    = VkDescriptorSetLayoutCreateInfo.calloc(stack);
            createInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO);

            createInfo.pBindings(pBindings);

            int retCode = VK10.vkCreateDescriptorSetLayout(
                    logicalDevice, createInfo, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create descriptor-set layout");
            descriptorSetLayoutHandle = pHandle.get(0);
        }
    }

    /**
     * Create a logical device in the application's main window.
     */
    private static void createLogicalDevice() {
        logicalDevice = physicalDevice.createLogicalDevice(
                surfaceHandle, enableDebugging);
        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Allocate a buffer to receive various pointers:
            PointerBuffer pPointer = stack.mallocPointer(1);

            // Obtain access to the graphics queue:
            int familyIndex = queueFamilies.graphics();
            int queueIndex = 0; // index within the queue family
            VK10.vkGetDeviceQueue(
                    logicalDevice, familyIndex, queueIndex, pPointer);
            long queueHandle = pPointer.get(0);
            graphicsQueue = new VkQueue(queueHandle, logicalDevice);

            // Obtain access to the presentation queue:
            familyIndex = queueFamilies.presentation();
            queueIndex = 0; // index within the queue family
            VK10.vkGetDeviceQueue(
                    logicalDevice, familyIndex, queueIndex, pPointer);
            queueHandle = pPointer.get(0);
            presentationQueue = new VkQueue(queueHandle, logicalDevice);
        }
    }

    /**
     * Create the graphics pipeline layout.
     *
     * @return the handle of the new layout
     */
    private static long createPipelineLayout() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Create the pipeline layout:
            VkPipelineLayoutCreateInfo plCreateInfo
                    = VkPipelineLayoutCreateInfo.calloc(stack);
            plCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);

            plCreateInfo.pPushConstantRanges(null);

            LongBuffer pSetLayouts = stack.longs(descriptorSetLayoutHandle);
            plCreateInfo.pSetLayouts(pSetLayouts);
            plCreateInfo.setLayoutCount(1);

            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreatePipelineLayout(
                    logicalDevice, plCreateInfo, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create pipeline layout");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Create a display surface in the application's main window.
     */
    private static void createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Allocate a buffer to receieve the handle of the new surface:
            LongBuffer pHandle = stack.mallocLong(1);

            int retCode = GLFWVulkan.glfwCreateWindowSurface(
                    vkInstance, windowHandle, defaultAllocator, pHandle);
            Utils.checkForError(retCode, "create window surface");
            surfaceHandle = pHandle.get(0);
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
     * Create a texture sampler for the current logical device.
     */
    private static void createTextureSampler() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack);
            samplerInfo.sType(VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO);

            samplerInfo.addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT);
            samplerInfo.anisotropyEnable(true);
            samplerInfo.borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK);
            samplerInfo.compareEnable(false);
            samplerInfo.compareOp(VK10.VK_COMPARE_OP_ALWAYS);
            samplerInfo.magFilter(VK10.VK_FILTER_LINEAR);
            samplerInfo.maxAnisotropy(16f);
            samplerInfo.maxLod(31f);
            samplerInfo.minFilter(VK10.VK_FILTER_LINEAR);
            samplerInfo.minLod(0f);
            samplerInfo.mipLodBias(0f);
            samplerInfo.mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR);
            samplerInfo.unnormalizedCoordinates(false);

            LongBuffer pTextureSampler = stack.mallocLong(1);
            int retCode = VK10.vkCreateSampler(logicalDevice, samplerInfo,
                    defaultAllocator, pTextureSampler);
            Utils.checkForError(retCode, "create texture sampler");
            samplerHandle = pTextureSampler.get(0);
        }
    }

    /**
     * Create a Vulkan instance to provide the application with access to the
     * Vulkan API.
     *
     * @param appName the name of the application (not null)
     * @param appVersion the version number of the application
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

            appInfo.pEngineName(stack.UTF8Safe(engineName));
            appInfo.engineVersion(engineVersion);

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
        int war = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
        if (severity > war) {
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
     * Callback to trigger a resize of the frame buffers.
     *
     * @param window the affected window
     * @param width the new framebuffer width
     * @param height the new framebuffer height
     */
    private static void frameBufferResizeCallback(
            long window, int width, int height) {
        needsResize = true;
    }

    /**
     * Initialize GLFW and create a main window for the application.
     *
     * @param initialTitle the initial text for the window's title bar (not
     * null)
     */
    private static void initializeGlfw(String initialTitle) {
        if (enableDebugging) {
            Configuration.DEBUG.set(true);
            Configuration.DEBUG_FUNCTIONS.set(true);
            Configuration.DEBUG_LOADER.set(true);
            Configuration.DEBUG_MEMORY_ALLOCATOR.set(true);
            Configuration.DEBUG_MEMORY_ALLOCATOR_INTERNAL.set(true);
            Configuration.DEBUG_MEMORY_ALLOCATOR_FAST.set(true);
            Configuration.DEBUG_STACK.set(true);
        }

        // Report GLFW errors to System.err:
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        /*
         * GLFW was designed for use with OpenGL, so we explicitly tell it
         * not to create an OpenGL context.
         */
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        // Create the main window:
        windowHandle = GLFW.glfwCreateWindow(
                frameBufferWidth, frameBufferHeight, initialTitle,
                MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create a GLFW window");
        }

        // Request callback when the frame buffer of the main window is resized:
        GLFW.glfwSetFramebufferSizeCallback(
                windowHandle, BaseApplication::frameBufferResizeCallback);
    }

    /**
     * Initialize the Vulkan API.
     *
     * @param appName the name of the application
     * @param appVersion the application's version numbers
     */
    private static void initializeVulkan(String appName, int appVersion) {
        createVkInstance(appName, appVersion);
        createSurface();
        selectPhysicalDevice();
        depthBufferFormat = chooseDepthBufferFormat();
        createLogicalDevice();
        createCommandPool();

        String resourceName = "/Models/viking_room/viking_room.obj";
        int flags = Assimp.aiProcess_DropNormals | Assimp.aiProcess_FlipUVs;
        List<Integer> indices = null;
        List<Vertex> vertices = new ArrayList<>();
        AssimpUtils.extractTriangles(resourceName, flags, indices, vertices);
        sampleMesh = Mesh.newInstance(vertices);

        sampleTexture = new Texture("/Models/viking_room/viking_room.png");
        createTextureSampler(); // depends on the logical device
        createDescriptorSetLayout(); // depends on the logical device
        pipelineLayoutHandle = createPipelineLayout();

        shaderProgram = getProgram("Debug/HelloVSport");

        createChainResources();
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
     * @param stack for memory allocation (not null)
     * @return a temporary buffer containing the names of all instance
     * extensions required by this application
     */
    private static PointerBuffer listRequiredInstanceExtensions(
            MemoryStack stack) {
        PointerBuffer glfwRequirements
                = GLFWVulkan.glfwGetRequiredInstanceExtensions();

        PointerBuffer result;
        if (enableDebugging) {
            // Vulkan debug utils require one additional instance extension:
            int numExtensions = glfwRequirements.capacity() + 1;
            result = stack.mallocPointer(numExtensions);
            result.put(glfwRequirements);
            ByteBuffer utf8Name = stack.UTF8(
                    EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
            result.put(utf8Name);
            result.rewind();

        } else {
            result = glfwRequirements;
        }

        return result;
    }

    /**
     * The application's main update loop.
     */
    private static void mainUpdateLoop() {
        while (!GLFW.glfwWindowShouldClose(windowHandle)) {
            GLFW.glfwPollEvents();

            Frame frame = inFlightFrames[currentFrameIndex];
            renderFrame(frame);
        }
    }

    /**
     * Record a command buffer for each image in the main swapchain.
     *
     * @param numImages the number of images in the swap chain
     */
    private static void recordCommandBuffers(int numImages) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Begin recording:
            VkCommandBufferBeginInfo beginInfo
                    = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);

            VkRenderPassBeginInfo renderPassInfo
                    = VkRenderPassBeginInfo.calloc(stack);
            renderPassInfo.sType(VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO);

            long renderPassHandle = chainResources.passHandle();
            renderPassInfo.renderPass(renderPassHandle);

            VkRect2D renderArea = VkRect2D.calloc(stack);
            renderArea.offset(VkOffset2D.calloc(stack).set(0, 0));
            VkExtent2D frameBufferExtent
                    = chainResources.framebufferExtent(stack);
            renderArea.extent(frameBufferExtent);
            renderPassInfo.renderArea(renderArea);

            VkClearValue.Buffer pClearValues = VkClearValue.calloc(2, stack);
            VkClearColorValue colorClearValue = pClearValues.get(0).color();
            colorClearValue.float32(stack.floats(0f, 0f, 0f, 1f));
            VkClearDepthStencilValue dsClearValue
                    = pClearValues.get(1).depthStencil();
            dsClearValue.set(1f, 0);
            renderPassInfo.pClearValues(pClearValues);

            // Record a command buffer for each image in the main swapchain:
            for (int imageIndex = 0; imageIndex < numImages; ++imageIndex) {
                VkCommandBuffer commandBuffer = commandBuffers.get(imageIndex);

                int retCode
                        = VK10.vkBeginCommandBuffer(commandBuffer, beginInfo);
                Utils.checkForError(retCode, "begin recording commands");

                // command to begin a render pass on the corresponding image:
                long frameBufferHandle
                        = chainResources.framebufferHandle(imageIndex);
                renderPassInfo.framebuffer(frameBufferHandle);
                VK10.vkCmdBeginRenderPass(commandBuffer, renderPassInfo,
                        VK10.VK_SUBPASS_CONTENTS_INLINE);

                // command to bind the graphics pipeline:
                long pipelineHandle = chainResources.pipelineHandle();
                VK10.vkCmdBindPipeline(commandBuffer,
                        VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineHandle);

                // command to bind the vertex buffers:
                int firstBinding = 0;
                int numAttributes = sampleMesh.countAttributes();
                LongBuffer pBufferHandles
                        = sampleMesh.generateBufferHandles(stack);
                LongBuffer pOffsets = stack.callocLong(numAttributes);
                VK10.vkCmdBindVertexBuffers(
                        commandBuffer, firstBinding, pBufferHandles, pOffsets);

                if (sampleMesh.isIndexed()) {
                    // command to bind the index buffer:
                    IndexBuffer indexBuffer = sampleMesh.getIndexBuffer();
                    int startOffset = 0;
                    VK10.vkCmdBindIndexBuffer(
                            commandBuffer, indexBuffer.handle(), startOffset,
                            indexBuffer.elementType());
                }

                // command to bind the uniforms:
                long descriptorSet
                        = chainResources.descriptorSetHandle(imageIndex);
                LongBuffer pDescriptorSets = stack.longs(descriptorSet);
                VK10.vkCmdBindDescriptorSets(
                        commandBuffer, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipelineLayoutHandle, 0, pDescriptorSets, null);

                // draw command:
                int firstVertex = 0;
                int firstInstance = 0;
                int instanceCount = 1;
                if (sampleMesh.isIndexed()) { // indexed draw:
                    int numIndices = sampleMesh.countIndexedVertices();
                    int firstIndex = 0;
                    VK10.vkCmdDrawIndexed(
                            commandBuffer, numIndices, instanceCount,
                            firstIndex, firstVertex, firstInstance);

                } else { // non-indexed draw:
                    int numVertices = sampleMesh.countIndexedVertices();
                    VK10.vkCmdDraw(commandBuffer, numVertices, instanceCount,
                            firstVertex, firstInstance);
                }

                // command to end the render pass:
                VK10.vkCmdEndRenderPass(commandBuffer);

                retCode = VK10.vkEndCommandBuffer(commandBuffer);
                Utils.checkForError(retCode, "end recording command");
            }
        }
    }

    /**
     * Re-create the main swapchain and any resources that depend on it or the
     * extent of its frame buffers.
     */
    private static void recreateChainResources() {
        needsResize = false;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.ints(0);
            IntBuffer height = stack.ints(0);

            while (width.get(0) == 0 && height.get(0) == 0) {
                GLFW.glfwGetFramebufferSize(windowHandle, width, height);
                GLFW.glfwWaitEvents();
            }
        }

        if (logicalDevice != null) {
            // Wait for all operations on the logical device to complete:
            VK10.vkDeviceWaitIdle(logicalDevice);
        }

        destroyChainResources();
        createChainResources();
    }

    /**
     * Render the specified frame in the update loop.
     *
     * @param frame the frame to render (not null)
     */
    private static void renderFrame(Frame frame) {
        long chainHandle = chainResources.chainHandle();
        long fenceHandle = frame.fenceHandle();
        long imageAvailableSemaphoreHandle
                = frame.imageAvailableSemaphoreHandle();
        long renderFinishedSemaphoreHandle
                = frame.renderFinishedSemaphoreHandle();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pImageIndex = stack.mallocInt(1);
            LongBuffer pChainHandle = stack.longs(chainHandle);
            LongBuffer pSignalSemaphores = stack.mallocLong(1);
            LongBuffer pWaitSemaphores = stack.mallocLong(1);
            PointerBuffer pCommandBuffer = stack.mallocPointer(1);

            // Wait for the GPU to signal completion of the previous frame:
            frame.waitForFence();

            // Acquire the next image from the swapchain:
            long fenceToSignalHandle = VK10.VK_NULL_HANDLE;
            int retCode = KHRSwapchain.vkAcquireNextImageKHR(logicalDevice,
                    chainHandle, noTimeout, imageAvailableSemaphoreHandle,
                    fenceToSignalHandle, pImageIndex);
            if (retCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || needsResize) {
                recreateChainResources();
                return;
            }
            Utils.checkForError(retCode, "acquire next image");
            int imageIndex = pImageIndex.get(0);

            updateUniformBuffer(imageIndex);

            Frame inFlightFrame = framesInFlight.get(imageIndex);
            if (inFlightFrame != null) {
                inFlightFrame.waitForFence();
            }
            framesInFlight.put(imageIndex, frame);

            // info to submit a command buffer to the graphics queue:
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO);

            submitInfo.waitSemaphoreCount(1);

            VkCommandBuffer commandBuffer = commandBuffers.get(imageIndex);
            pCommandBuffer.put(0, commandBuffer);
            submitInfo.pCommandBuffers(pCommandBuffer);

            pSignalSemaphores.put(0, renderFinishedSemaphoreHandle);
            submitInfo.pSignalSemaphores(pSignalSemaphores);

            IntBuffer pMask = stack.ints(
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            submitInfo.pWaitDstStageMask(pMask);

            pWaitSemaphores.put(0, imageAvailableSemaphoreHandle);
            submitInfo.pWaitSemaphores(pWaitSemaphores);

            // Reset fence and submit pre-recorded commands to graphics queue:
            VK10.vkResetFences(logicalDevice, fenceHandle);
            retCode = VK10.vkQueueSubmit(
                    graphicsQueue, submitInfo, fenceHandle);
            Utils.checkForError(retCode, "submit draw command");

            // Enqueue the image for presentation:
            VkPresentInfoKHR presentationInfo = VkPresentInfoKHR.calloc(stack);
            presentationInfo.sType(
                    KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR);

            presentationInfo.pImageIndices(pImageIndex);
            presentationInfo.pSwapchains(pChainHandle);
            presentationInfo.swapchainCount(pChainHandle.capacity());

            pWaitSemaphores.put(0, renderFinishedSemaphoreHandle);
            presentationInfo.pWaitSemaphores(pWaitSemaphores);

            retCode = KHRSwapchain.vkQueuePresentKHR(
                    presentationQueue, presentationInfo);
            if (retCode == KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                    || needsResize) {
                recreateChainResources();
                return;
            }
            Utils.checkForError(retCode, "enqueue image for presentation");

            currentFrameIndex = (currentFrameIndex + 1) % maxFramesInFlight;
        }
    }

    /**
     * Select the physical device best suited to displaying the main surface.
     */
    private static void selectPhysicalDevice() {
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

            PointerBuffer pPointers = stack.mallocPointer(numDevices);
            retCode = VK10.vkEnumeratePhysicalDevices(
                    vkInstance, pCount, pPointers);
            Utils.checkForError(retCode, "enumerate physicsl devices");

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
                cleanup();
                System.exit(0);
            }
        }
    }

    /**
     * Update the Uniform Buffer Object (UBO) of the indexed image.
     *
     * @param imageIndex index of the target image among the swap-chain images.
     */
    private static void updateUniformBuffer(int imageIndex) {
        ByteBuffer byteBuffer = chainResources.getUbo(imageIndex).getData();
        uniformValues.writeTo(byteBuffer);
    }
}
