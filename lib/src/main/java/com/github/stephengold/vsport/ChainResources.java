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
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import jme3utilities.Validate;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

/**
 * Encapsulate Vulkan resources that depend on the swap chain (and thus,
 * indirectly, on the logical device).
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class ChainResources {
    // *************************************************************************
    // fields

    /**
     * transient color attachment for multi-sample framebuffers
     */
    private Attachment colorAttachment;
    /**
     * depth attachment for framebuffers
     */
    private Attachment depthAttachment;
    /**
     * image format (shared by all images in the swapchain)
     */
    final private int imageFormat;
    /**
     * number of presentation images in the swapchain (&gt;0)
     */
    final private int numImages;
    /**
     * command sequences (one for each presentation image in the swapchain)
     */
    final private List<CommandSequence> sequenceList = new ArrayList<>(3);
    /**
     * render passes (one for each presentation image in the swapchain)
     */
    final private List<Pass> passList = new ArrayList<>(3);
    /**
     * {@code VkSwapchainKHR} handle
     */
    private long chainHandle = VK10.VK_NULL_HANDLE;
    /**
     * {@code VkRenderPass} handle
     */
    private long passHandle = VK10.VK_NULL_HANDLE;
    /**
     * {@code VkDescriptorPool} handle
     */
    private long poolHandle = VK10.VK_NULL_HANDLE;
    /**
     * framebuffer dimensions (in pixels, shared by all presentation images)
     */
    final private VkExtent2D framebufferExtent = VkExtent2D.create();
    // *************************************************************************
    // constructors

    /**
     * Instantiate resources for the specified configuration.
     *
     * @param surface the features of the active {@code VkSurfaceKHR} (not null)
     * @param descriptorSetLayoutHandle the handle of the descriptor-set layout
     * (not null)
     * @param desiredWidth the desired framebuffer width (in pixels, &gt;0)
     * @param desiredHeight the desired framebuffer height (in pixels, &gt;0)
     * @param depthFormat the desired depth-buffer format
     * @param pipelineLayoutHandle the handle of the graphics-pipeline layout
     * (not null)
     */
    ChainResources(SurfaceSummary surface, long descriptorSetLayoutHandle,
            int desiredWidth, int desiredHeight,
            int depthFormat, long pipelineLayoutHandle) {
        Validate.nonNull(surface, "surface");
        Validate.nonZero(
                descriptorSetLayoutHandle, "descriptor-set layout handle");
        Validate.nonZero(pipelineLayoutHandle, "pipeline-layout handle");

        this.numImages = chooseNumImages(surface);
        System.out.println("numImages = " + numImages);

        this.poolHandle = createPool(numImages * 1600); // TODO plenty for now

        VkSurfaceFormatKHR surfaceFormat = surface.chooseSurfaceFormat();
        this.imageFormat = surfaceFormat.format();

        PhysicalDevice physicalDevice = Internals.getPhysicalDevice();
        long surfaceHandle = surface.handle();
        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);

        surface.chooseFramebufferExtent(
                desiredWidth, desiredHeight, framebufferExtent);

        int numSamples = Internals.countMsaaSamples();
        if (numSamples == 1) { // render directly to a presentation image:
            this.colorAttachment = null;
        } else {
            // render to transient color buffer, then resolve for presentation:
            this.colorAttachment
                    = new Attachment(imageFormat, framebufferExtent,
                            VK10.VK_IMAGE_ASPECT_COLOR_BIT, numSamples);
        }
        this.depthAttachment = new Attachment(depthFormat, framebufferExtent,
                VK10.VK_IMAGE_ASPECT_DEPTH_BIT, numSamples);

        this.chainHandle = createChain(framebufferExtent, imageFormat,
                numImages, surface, surfaceFormat, queueFamilies);
        this.passHandle
                = createPass(imageFormat, colorAttachment, depthAttachment);

        long[] imageHandles = listImages(chainHandle);
        for (long imageHandle : imageHandles) {
            CommandSequence sequence = new CommandSequence();
            sequenceList.add(sequence);

            Pass pass = new Pass(
                    imageHandle, poolHandle, descriptorSetLayoutHandle,
                    imageFormat, colorAttachment, depthAttachment, passHandle,
                    framebufferExtent);
            passList.add(pass);
        }
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Access the swap chain.
     *
     * @return the handle of the pre-existing {@code VkSwapchainKHR} (not null)
     */
    long chainHandle() {
        assert chainHandle != VK10.VK_NULL_HANDLE;
        return chainHandle;
    }

    /**
     * Count the presentation images in the swap chain.
     *
     * @return the count (&gt;0)
     */
    int countImages() {
        return numImages;
    }

    /**
     * Destroy all resources owned by this instance.
     */
    void destroy() {
        VkDevice vkDevice = Internals.getVkDevice();
        VkAllocationCallbacks allocator = Internals.findAllocator();

        // Destroy resources in the reverse of the order they were created:
        for (Pass pass : passList) {
            pass.destroy();
        }

        if (passHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyRenderPass(vkDevice, passHandle, allocator);
            this.passHandle = VK10.VK_NULL_HANDLE;
        }

        if (chainHandle != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(
                    vkDevice, chainHandle, allocator);
            this.chainHandle = VK10.VK_NULL_HANDLE;
        }

        if (depthAttachment != null) {
            this.depthAttachment = depthAttachment.destroy();
        }
        if (colorAttachment != null) {
            this.colorAttachment = colorAttachment.destroy();
        }

        if (poolHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorPool(vkDevice, poolHandle, allocator);
            this.poolHandle = VK10.VK_NULL_HANDLE;
        }
    }

    /**
     * Return the extent of each framebuffer.
     *
     * @param stack for allocating temporary host buffers (not null)
     * @return a temporary VkExtent2D
     */
    VkExtent2D framebufferExtent(MemoryStack stack) {
        VkExtent2D result = VkExtent2D.malloc(stack);
        result.set(framebufferExtent);

        return result;
    }

    /**
     * Return the height of each framebuffer.
     *
     * @return the height in pixels
     */
    int framebufferHeight() {
        int result = framebufferExtent.height();
        return result;
    }

    /**
     * Return the width of each framebuffer.
     *
     * @return the width in pixels
     */
    int framebufferWidth() {
        int result = framebufferExtent.width();
        return result;
    }

    /**
     * Access the render-pass resources for the specified presentation image.
     *
     * @param imageIndex the index of the corresponding image (&ge;0)
     * @return a new or pre-existing instance (not null)
     */
    Pass getPass(int imageIndex) {
        Pass result = passList.get(imageIndex);

        assert result != null;
        return result;
    }

    /**
     * Access the command sequence for the specified presentation image.
     *
     * @param imageIndex the index of the corresponding image (&ge;0)
     * @return the pre-existing instance (not null)
     */
    CommandSequence getSequence(int imageIndex) {
        CommandSequence result = sequenceList.get(imageIndex);

        assert result != null;
        return result;
    }

    /**
     * Access the render pass.
     *
     * @return the handle of the pre-existing {@code VkRenderPass} (not null)
     */
    long passHandle() {
        assert passHandle != VK10.VK_NULL_HANDLE;
        return passHandle;
    }
    // *************************************************************************
    // private methods

    /**
     * Choose the number of images in the chain.
     *
     * @param surface the features of a active surface (not null)
     * @return the count (&gt;0)
     */
    private static int chooseNumImages(SurfaceSummary surface) {
        /*
         * Minimizing the number of images in the swapchain would
         * sometimes cause the application to wait on the driver
         * when acquiring an image to render to.  To avoid waiting,
         * request one more than the minimum number.
         */
        int minImageCount = surface.minImageCount();
        int numImages = minImageCount + 1;

        // If there's an upper limit on images, don't exceed it:
        int maxImageCount = surface.maxImageCount();
        if (maxImageCount > 0 && numImages > maxImageCount) {
            numImages = maxImageCount;
        }

        assert numImages >= 1 : numImages;
        return numImages;
    }

    /**
     * Create a swap chain.
     *
     * @param framebufferExtent the desired framebuffer dimensions (not null)
     * @param imageFormat the desired image format
     * @param numImages the desired number of images
     * @param surface the features of a active VkSurfaceKHR (not null)
     * @param surfaceFormat the desired surface format (not null)
     * @param queueFamilies a summary of the available queue families (not null)
     * @return the handle of the new {@code VkSwapchainKHR}
     */
    private static long createChain(VkExtent2D framebufferExtent,
            int imageFormat, int numImages, SurfaceSummary surface,
            VkSurfaceFormatKHR surfaceFormat,
            QueueFamilySummary queueFamilies) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSwapchainCreateInfoKHR createInfo
                    = VkSwapchainCreateInfoKHR.calloc(stack);
            createInfo.sType(
                    KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR);

            createInfo.clipped(true); // Don't color obscured pixels.

            // Ignore the alpha channel when blending with other windows:
            createInfo.compositeAlpha(
                    KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR);

            createInfo.imageArrayLayers(1);
            createInfo.imageExtent(framebufferExtent);
            createInfo.imageFormat(imageFormat);
            createInfo.minImageCount(numImages);
            createInfo.oldSwapchain(VK10.VK_NULL_HANDLE);

            // The app will render directly to images in the swapchain:
            createInfo.imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);

            int preTransform = surface.currentTransform();
            createInfo.preTransform(preTransform);

            long surfaceHandle = surface.handle();
            createInfo.surface(surfaceHandle);

            int colorSpace = surfaceFormat.colorSpace();
            createInfo.imageColorSpace(colorSpace);

            IntBuffer pListDistinct = queueFamilies.pListDistinct(stack);
            int numDistinctFamilies = pListDistinct.capacity();
            if (numDistinctFamilies == 2) {
                createInfo.imageSharingMode(VK10.VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(pListDistinct);
            } else {
                createInfo.imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);
            }

            int presentMode = surface.choosePresentationMode();
            createInfo.presentMode(presentMode);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = KHRSwapchain.vkCreateSwapchainKHR(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create a swapchain");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Create a render pass with the specified properties.
     *
     * @param imageFormat the format of images in the swap chain
     * @param color the (transient) color attachment for each framebuffer (may
     * be null)
     * @param depth the depth attachment for each framebuffer (not null)
     * @return the handle of the new {@code VkRenderPass} (not null)
     */
    private static long createPass(
            int imageFormat, Attachment color, Attachment depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int numAttachments;
            if (color == null) {
                numAttachments = 2;
            } else {
                numAttachments = 3;
            }
            VkAttachmentDescription.Buffer pDescriptions
                    = VkAttachmentDescription.calloc(numAttachments, stack);
            VkAttachmentReference.Buffer pReferences
                    = VkAttachmentReference.calloc(numAttachments, stack);

            VkAttachmentDescription colorAttachment = pDescriptions.get(0);
            if (color == null) {
                // [0] a single color buffer for presentation, no multisampling:
                colorAttachment.finalLayout(
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
                colorAttachment.format(imageFormat);
                colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                colorAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);

                // no stencil operations:
                colorAttachment.stencilLoadOp(
                        VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                colorAttachment.stencilStoreOp(
                        VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);

                // Clear the color buffer to black before each frame:
                colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
                colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);
            } else {
                // [0] a color buffer with multisampling:
                color.describe(colorAttachment);
            }

            VkAttachmentReference colorAttachmentRef = pReferences.get(0);
            colorAttachmentRef.attachment(0);
            colorAttachmentRef.layout(
                    VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference.Buffer pColorRefs
                    = VkAttachmentReference.calloc(1, stack);
            pColorRefs.put(0, colorAttachmentRef);

            // [1] a single depth buffer:
            VkAttachmentDescription depthAttachment = pDescriptions.get(1);
            depth.describe(depthAttachment);
            VkAttachmentReference depthAttachmentRef = pReferences.get(1);
            depthAttachmentRef.attachment(1);
            depthAttachmentRef.layout(
                    VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            VkAttachmentReference.Buffer pResolveRefs = null;
            if (color != null) {
                // [2] Resolve the multisampled color buffer for presentation:
                VkAttachmentDescription resolveAttachment
                        = pDescriptions.get(2);
                resolveAttachment.finalLayout(
                        KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
                resolveAttachment.format(imageFormat);
                resolveAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
                resolveAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);

                resolveAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                resolveAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);

                // no stencil operations:
                resolveAttachment.stencilLoadOp(
                        VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
                resolveAttachment.stencilStoreOp(
                        VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);

                VkAttachmentReference resolveAttachmentRef = pReferences.get(2);
                resolveAttachmentRef.attachment(2);
                resolveAttachmentRef.layout(
                        VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
                pResolveRefs = VkAttachmentReference.calloc(1, stack);
                pResolveRefs.put(0, resolveAttachmentRef);
            }

            // a single subpass:
            VkSubpassDescription.Buffer subpasses
                    = VkSubpassDescription.calloc(1, stack);
            subpasses.colorAttachmentCount(1);
            subpasses.pColorAttachments(pColorRefs);
            subpasses.pDepthStencilAttachment(depthAttachmentRef);
            subpasses.pResolveAttachments(pResolveRefs);
            subpasses.pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS);

            // Create a sub-pass dependency:
            VkSubpassDependency.Buffer pDependency
                    = VkSubpassDependency.calloc(1, stack);
            pDependency.dstAccessMask(
                    VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT
                    | VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
            pDependency.dstStageMask(
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            pDependency.dstSubpass(0);
            pDependency.srcAccessMask(0x0);
            pDependency.srcStageMask(
                    VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);
            pDependency.srcSubpass(VK10.VK_SUBPASS_EXTERNAL);

            // Create the render pass with its dependency:
            VkRenderPassCreateInfo createInfo
                    = VkRenderPassCreateInfo.calloc(stack);
            createInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO);

            createInfo.pAttachments(pDescriptions);
            createInfo.pDependencies(pDependency);
            createInfo.pSubpasses(subpasses);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateRenderPass(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create render pass");
            long result = pHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }

    /**
     * Create the descriptor-set pool for UBO descriptors and sampler
     * descriptors.
     *
     * @param poolSize the desired number of descriptors of each type
     * @return the handle of the new {@code VkDescriptorPool} (not null)
     */
    private static long createPool(int poolSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer pPoolSizes
                    = VkDescriptorPoolSize.calloc(2, stack);

            // The UBO descriptor pool will contain poolSize descriptors:
            VkDescriptorPoolSize uboPool = pPoolSizes.get(0);
            uboPool.type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uboPool.descriptorCount(poolSize);

            // The sampler descriptor pool will contain poolSize descriptors:
            VkDescriptorPoolSize samplerPool = pPoolSizes.get(1);
            samplerPool.type(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            samplerPool.descriptorCount(poolSize);

            // Create the descriptor-set pool:
            VkDescriptorPoolCreateInfo createInfo
                    = VkDescriptorPoolCreateInfo.calloc(stack);
            createInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO);

            createInfo.flags(0x0); // descriptor sets are never freed
            createInfo.maxSets(poolSize);
            createInfo.pPoolSizes(pPoolSizes);

            VkDevice vkDevice = Internals.getVkDevice();
            VkAllocationCallbacks allocator = Internals.findAllocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateDescriptorPool(
                    vkDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create descriptor-set pool");
            long result = pHandle.get(0);

            assert result != VK10.VK_NULL_HANDLE;
            return result;
        }
    }

    /**
     * Enumerate the images created by a KHRSwapchain.
     *
     * @param chainHandle the handle of the {@code VkSwapchainKHR} (not null)
     * @return a new array of {@code VkImage} handles
     */
    private static long[] listImages(long chainHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the images:
            VkDevice vkDevice = Internals.getVkDevice();
            IntBuffer pCount = stack.mallocInt(1);
            int retCode = KHRSwapchain.vkGetSwapchainImagesKHR(
                    vkDevice, chainHandle, pCount, null);
            Utils.checkForError(retCode, "count presentation images");
            int numImages = pCount.get(0);

            // Enumerate the presentation images:
            LongBuffer pHandles = stack.mallocLong(numImages);
            retCode = KHRSwapchain.vkGetSwapchainImagesKHR(
                    vkDevice, chainHandle, pCount, pHandles);
            Utils.checkForError(retCode, "enumerate presentation images");

            // Collect the image handles in an array:
            long[] result = new long[numImages];
            for (int imageIndex = 0; imageIndex < numImages; ++imageIndex) {
                long handle = pHandles.get(imageIndex);
                result[imageIndex] = handle;
            }

            return result;
        }
    }
}
