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
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkCopyDescriptorSet;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
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
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/**
 * Encapsulate Vulkan resources that depend on the swap chain.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
class ChainResources {
    // *************************************************************************
    // fields

    /**
     * transient color attachment for framebuffers (may be null)
     */
    private Attachment colorAttachment;
    /**
     * depth attachment for framebuffers
     */
    private Attachment depthAttachment;
    /**
     * image format (shared by all images in the chain)
     */
    final private int imageFormat;
    /**
     * number of images in the chain (&ge;1)
     */
    final private int numImages;
    /**
     * uniform buffer objects (one per VkImage in the chain)
     */
    final private List<BufferResource> ubos = new ArrayList<>(4);
    /**
     * handles of the descriptor sets (one per VkImage in the chain)
     */
    private List<Long> descriptorSetHandles;
    /**
     * handles of framebuffers (one per VkImage in the chain)
     */
    private List<Long> framebufferHandles;
    /**
     * VkImage handles for color buffers (acquired from KHRSwapchain)
     */
    final private List<Long> imageHandles;
    /**
     * VkImageView handles (one per VkImage in the chain)
     */
    private List<Long> viewHandles;
    /**
     * handle of the VkSwapchainKHR
     */
    private long chainHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the VkRenderPass
     */
    private long passHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the graphics VkPipeline
     */
    private long pipelineHandle = VK10.VK_NULL_HANDLE;
    /**
     * handle of the descriptor-set pool
     */
    private long poolHandle = VK10.VK_NULL_HANDLE;
    /**
     * framebuffer dimensions (in pixels, shared by all images in the chain)
     */
    final private VkExtent2D framebufferExtent = VkExtent2D.create();
    // *************************************************************************
    // constructors

    /**
     * Instantiate resources for the specified configuration.
     *
     * @param surface the features of the active VkSurfaceKHR (not null)
     * @param descriptorSetLayoutHandle the handle of the descriptor-set layout
     * for the UBO
     * @param desiredWidth the desired framebuffer width (in pixels)
     * @param desiredHeight the desired framebuffer height (in pixels)
     * @param depthFormat the depth-buffer format
     * @param samplerHandle the handle of the VkSampler for textures
     * @param pipelineLayoutHandle the handle of the graphics-pipeline layout
     * @param mesh the mesh to render (not null)
     * @param shaderProgram the shader program to use (not null)
     * @param texture the texture to be used in rendering (not null)
     */
    ChainResources(SurfaceSummary surface, long descriptorSetLayoutHandle,
            int desiredWidth, int desiredHeight,
            int depthFormat, long samplerHandle, long pipelineLayoutHandle,
            Mesh mesh, ShaderProgram shaderProgram, Texture texture) {
        this.numImages = chooseNumImages(surface);
        addUbos(numImages, ubos);

        this.poolHandle = createPool(numImages);
        this.descriptorSetHandles = allocateDescriptorSets(
                numImages, descriptorSetLayoutHandle, poolHandle);

        VkSurfaceFormatKHR surfaceFormat = surface.chooseSurfaceFormat();
        this.imageFormat = surfaceFormat.format();

        PhysicalDevice physicalDevice = BaseApplication.getPhysicalDevice();
        long surfaceHandle = surface.handle();
        QueueFamilySummary queueFamilies
                = physicalDevice.summarizeFamilies(surfaceHandle);

        surface.chooseFramebufferExtent(
                desiredWidth, desiredHeight, framebufferExtent);
        this.colorAttachment = null;
        this.depthAttachment = new Attachment(depthFormat, framebufferExtent,
                VK10.VK_IMAGE_ASPECT_DEPTH_BIT, VK10.VK_SAMPLE_COUNT_1_BIT);

        this.chainHandle = createChain(framebufferExtent, imageFormat,
                numImages, surface, surfaceFormat, queueFamilies);
        this.imageHandles = listImages(chainHandle);
        this.viewHandles = createImageViews(imageHandles, imageFormat);

        this.passHandle = createPass(
                imageFormat, colorAttachment, depthAttachment);
        this.framebufferHandles = createFramebuffers(
                viewHandles, colorAttachment, depthAttachment, passHandle,
                framebufferExtent);

        this.pipelineHandle = createPipeline(pipelineLayoutHandle,
                framebufferExtent, passHandle, mesh, shaderProgram);

        updateDescriptorSets(
                texture, samplerHandle, ubos, descriptorSetHandles);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the swap-chain handle.
     *
     * @return the handle of the pre-existing VkSwapchainKHR
     */
    long chainHandle() {
        return chainHandle;
    }

    /**
     * Count the images in the chain.
     *
     * @return the count (&ge;1)
     */
    int countImages() {
        return numImages;
    }

    /**
     * Return the indexed descriptor-set handle.
     *
     * @param imageIndex the index of the desired descriptor set
     * @return the handle of the pre-existing VkDescriptorSet
     */
    long descriptorSetHandle(int imageIndex) {
        long result = descriptorSetHandles.get(imageIndex);
        return result;
    }

    /**
     * Destroy all owned resources.
     */
    void destroy() {
        VkDevice logicalDevice = BaseApplication.getLogicalDevice();
        VkAllocationCallbacks allocator = BaseApplication.allocator();
        /*
         * Destroy resources in the reverse of the order they were created,
         * starting with the pipeline.
         */
        if (pipelineHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyPipeline(logicalDevice, pipelineHandle, allocator);
            pipelineHandle = VK10.VK_NULL_HANDLE;
        }

        if (framebufferHandles != null) {
            for (long handle : framebufferHandles) {
                VK10.vkDestroyFramebuffer(logicalDevice, handle, allocator);
            }
            this.framebufferHandles = null;
        }
        if (passHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyRenderPass(logicalDevice, passHandle, allocator);
            this.passHandle = VK10.VK_NULL_HANDLE;
        }

        if (viewHandles != null) {
            for (Long handle : viewHandles) {
                VK10.vkDestroyImageView(logicalDevice, handle, allocator);
            }
            this.viewHandles = null;
        }

        if (chainHandle != VK10.VK_NULL_HANDLE) {
            KHRSwapchain.vkDestroySwapchainKHR(
                    logicalDevice, chainHandle, allocator);
            this.chainHandle = VK10.VK_NULL_HANDLE;
        }

        if (depthAttachment != null) {
            depthAttachment.destroy();
            this.depthAttachment = null;
        }
        if (colorAttachment != null) {
            colorAttachment.destroy();
            this.colorAttachment = null;
        }

        descriptorSetHandles = null;
        if (poolHandle != VK10.VK_NULL_HANDLE) {
            VK10.vkDestroyDescriptorPool(logicalDevice, poolHandle, allocator);
            this.poolHandle = VK10.VK_NULL_HANDLE;
        }

        for (BufferResource ubo : ubos) {
            ubo.destroy();
        }
        ubos.clear();
    }

    /**
     * Return the extent of each framebuffer.
     *
     * @param stack for memory allocation (not null)
     * @return a temporary VkExtent2D
     */
    VkExtent2D framebufferExtent(MemoryStack stack) {
        VkExtent2D result = VkExtent2D.malloc(stack);
        result.set(framebufferExtent);

        return result;
    }

    /**
     * Return the handle of the indexed framebuffer.
     *
     * @param imageIndex the index of the desired framebuffer
     * @return the handle of the pre-existing VkFramebuffer
     */
    long framebufferHandle(int imageIndex) {
        long result = framebufferHandles.get(imageIndex);
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
     * Access the Uniform Buffer Object (UBO) for the indexed image.
     *
     * @param imageIndex the index of the desired UBO
     * @return a pre-existing instance
     */
    BufferResource getUbo(int imageIndex) {
        BufferResource result = ubos.get(imageIndex);
        return result;
    }

    /**
     * Return the render-pass handle.
     *
     * @return the handle of the pre-existing VkRenderPass (not VK_NULL_HANDLE)
     */
    long passHandle() {
        assert passHandle != VK10.VK_NULL_HANDLE;
        return passHandle;
    }

    /**
     * Return the graphics pipeline handle.
     *
     * @return the handle of the pre-existing VkPipeline (not VK_NULL_HANDLE)
     */
    long pipelineHandle() {
        assert pipelineHandle != VK10.VK_NULL_HANDLE;
        return pipelineHandle;
    }
    // *************************************************************************
    // private methods

    /**
     * Allocate uniform buffer objects (UBOs) as needed.
     *
     * @param numUbosNeeded the number of UBOs needed
     * @param addUbos storage for allocated UBOs (not null, added to)
     */
    private static void addUbos(
            int numUbosNeeded, List<BufferResource> addUbos) {
        numUbosNeeded -= addUbos.size();
        if (numUbosNeeded <= 0) {
            return;
        }

        int numBytes = UniformValues.numBytes();
        boolean staging = false;
        for (int i = 0; i < numUbosNeeded; ++i) {
            BufferResource ubo = new BufferResource(
                    numBytes, VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, staging);
            addUbos.add(ubo);
        }
    }

    /**
     * Allocate a descriptor set for each image in the chain.
     *
     * @param numImages the number of images in the chain
     * @param layoutHandle the handle of the desciptor-set layout to use
     * @param poolHandle the handle of the descriptor-set pool to use
     * @return a new list of VkDescriptorSet handles
     */
    private static List<Long> allocateDescriptorSets(
            int numImages, long layoutHandle, long poolHandle) {
        List<Long> result = new ArrayList<>(numImages);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // All descriptor sets will have the same layout:
            LongBuffer pLayoutHandles = stack.mallocLong(numImages);
            for (int setIndex = 0; setIndex < numImages; ++setIndex) {
                pLayoutHandles.put(setIndex, layoutHandle);
            }

            VkDescriptorSetAllocateInfo allocInfo
                    = VkDescriptorSetAllocateInfo.calloc(stack);
            allocInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO);

            allocInfo.descriptorPool(poolHandle);
            allocInfo.pSetLayouts(pLayoutHandles);

            LongBuffer pSetHandles = stack.mallocLong(numImages);
            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            int retCode = VK10.vkAllocateDescriptorSets(
                    logicalDevice, allocInfo, pSetHandles);
            Utils.checkForError(retCode, "allocate descriptor sets");

            // Collect the descriptor-set handles in a list:
            for (int setIndex = 0; setIndex < numImages; ++setIndex) {
                long setHandle = pSetHandles.get(setIndex);
                result.add(setHandle);
            }

            return result;
        }
    }

    /**
     * Choose the number of images in the chain.
     *
     * @param surface the features of a active VkSurfaceKHR (not null)
     * @return the count (&ge;1)
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
     * Create a VkSwapchainKHR during initialization.
     *
     * @param framebufferExtent the desired framebuffer dimensions (not null)
     * @param imageFormat the desired image format
     * @param numImages the desired number of images
     * @param surface the features of a active VkSurfaceKHR (not null)
     * @param surfaceFormat the desired surface format (not null)
     * @param queueFamilies a summary of the available queue families (not null)
     * @return the handle of the new VkSwapchainKHR
     */
    private static long createChain(VkExtent2D framebufferExtent,
            int imageFormat, int numImages, SurfaceSummary surface,
            VkSurfaceFormatKHR surfaceFormat,
            QueueFamilySummary queueFamilies) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // swap-chain creation information:
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

            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            VkAllocationCallbacks allocator = BaseApplication.allocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = KHRSwapchain.vkCreateSwapchainKHR(
                    logicalDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create a swapchain");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Create a framebuffer for each image in the chain.
     *
     * @param viewHandles the list of VkImageView handles for presentation (not
     * null)
     * @param color the (transient) color attachment for each framebuffer (may
     * be null)
     * @param depth the depth attachment for each framebuffer (not null)
     * @param renderPassHandle the handle of the VkRenderPass to use
     * @param extent the desired dimensions (in pixels, not null)
     * @return a new list of VkFramebuffer handles
     */
    private static List<Long> createFramebuffers(List<Long> viewHandles,
            Attachment color, Attachment depth,
            long renderPassHandle, VkExtent2D extent) {
        int numImages = viewHandles.size();
        List<Long> result = new ArrayList<>(numImages);

        long depthViewHandle = depth.viewHandle();
        int width = extent.width();
        int height = extent.height();
        VkDevice logicalDevice = BaseApplication.getLogicalDevice();
        VkAllocationCallbacks allocator = BaseApplication.allocator();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            // order of attachments must match that in createPass() below!
            LongBuffer pAttachmentHandles
                    = stack.longs(VK10.VK_NULL_HANDLE, depthViewHandle);
            LongBuffer pHandle = stack.mallocLong(1);

            // reusable Struct for framebuffer creation:
            VkFramebufferCreateInfo createInfo
                    = VkFramebufferCreateInfo.calloc(stack);
            createInfo.sType(VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO);

            createInfo.height(height);
            createInfo.layers(1);
            createInfo.pAttachments(pAttachmentHandles);
            createInfo.renderPass(renderPassHandle);
            createInfo.width(width);

            for (long viewHandle : viewHandles) {
                pAttachmentHandles.put(0, viewHandle);

                int retCode = VK10.vkCreateFramebuffer(
                        logicalDevice, createInfo, allocator, pHandle);
                Utils.checkForError(retCode, "create a framebuffer");
                long frameBufferHandle = pHandle.get(0);
                result.add(frameBufferHandle);
            }

            return result;
        }
    }

    /**
     * Create a view for each image in the specified list.
     *
     * @param imageHandles the list of VkImage handles (not null)
     * @param imageFormat the image format of the images
     * @return a new list of VkImageView handles
     */
    private static List<Long> createImageViews(
            List<Long> imageHandles, int imageFormat) {
        int numImages = imageHandles.size();
        int numMipLevels = 1;
        List<Long> result = new ArrayList<>(numImages);
        for (long imageHandle : imageHandles) {
            long viewHandle = BaseApplication.createImageView(imageHandle,
                    imageFormat, VK10.VK_IMAGE_ASPECT_COLOR_BIT, numMipLevels);
            result.add(viewHandle);
        }

        return result;
    }

    /**
     * Create a VkRenderPass for the specified formats.
     *
     * @param imageFormat the format of images in the swap chain
     * @param color the (transient) color attachment for each framebuffer
     * @param depth the depth attachment for each framebuffer (not null)
     * @return the handle of the new VkRenderPass
     */
    private static long createPass(
            int imageFormat, Attachment color, Attachment depth) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkAttachmentDescription.Buffer pDescriptions
                    = VkAttachmentDescription.calloc(2, stack);
            VkAttachmentReference.Buffer pReferences
                    = VkAttachmentReference.calloc(2, stack);

            // a single color buffer for presentation, without multisampling:
            VkAttachmentDescription colorAttachment = pDescriptions.get(0);
            colorAttachment.finalLayout(
                    KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            colorAttachment.format(imageFormat);
            colorAttachment.initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED);
            colorAttachment.samples(VK10.VK_SAMPLE_COUNT_1_BIT);

            // no stencil operations:
            colorAttachment.stencilLoadOp(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE);
            colorAttachment.stencilStoreOp(
                    VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE);

            // Clear the color buffer to black before each frame:
            colorAttachment.loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR);
            colorAttachment.storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE);

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

            // a single subpass:
            VkSubpassDescription.Buffer subpasses
                    = VkSubpassDescription.calloc(1, stack);
            subpasses.colorAttachmentCount(1);
            subpasses.pColorAttachments(pColorRefs);
            subpasses.pDepthStencilAttachment(depthAttachmentRef);
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

            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            VkAllocationCallbacks allocator = BaseApplication.allocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateRenderPass(
                    logicalDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create reander pass");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Create a graphics pipeline.
     *
     * @param pipelineLayoutHandle the handle of the graphics-pipeline layout to
     * use
     * @param framebufferExtent the framebuffer dimensions (not null)
     * @param passHandle the handle of the VkRenderPass to use
     * @param mesh the mesh to be rendered (not null)
     * @param shaderProgram the shader program to use (not null)
     * @return the handle of the new VkPipeline
     */
    private static long createPipeline(long pipelineLayoutHandle,
            VkExtent2D framebufferExtent, long passHandle, Mesh mesh,
            ShaderProgram shaderProgram) {
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

            // depth-stencil state:
            VkPipelineDepthStencilStateCreateInfo dssCreateInfo
                    = VkPipelineDepthStencilStateCreateInfo.calloc(stack);
            dssCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO
            );

            dssCreateInfo.depthTestEnable(true);
            dssCreateInfo.depthWriteEnable(true);
            dssCreateInfo.depthCompareOp(VK10.VK_COMPARE_OP_LESS);
            dssCreateInfo.depthBoundsTestEnable(false);
            dssCreateInfo.minDepthBounds(0f); // Optional
            dssCreateInfo.maxDepthBounds(1f); // Optional
            dssCreateInfo.stencilTestEnable(false);

            // input-assembly state:
            //    1. what kind of mesh to build (mesh mode/topology)
            //    2. whether primitive restart should be enabled
            VkPipelineInputAssemblyStateCreateInfo iasCreateInfo
                    = VkPipelineInputAssemblyStateCreateInfo.calloc(stack);
            iasCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO
            );

            iasCreateInfo.primitiveRestartEnable(false);
            iasCreateInfo.topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

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
            msCreateInfo.rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);
            msCreateInfo.sampleShadingEnable(false);

            // rasterization state:
            //    1. turns mesh primitives into fragments
            //    2. performs depth testing, face culling, and the scissor test
            //    3. fills polygons and/or edges (wireframe)
            //    4. determines front/back faces of polygons (winding)
            VkPipelineRasterizationStateCreateInfo rsCreateInfo
                    = VkPipelineRasterizationStateCreateInfo.calloc(stack);
            rsCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO
            );

            rsCreateInfo.cullMode(VK10.VK_CULL_MODE_BACK_BIT);
            rsCreateInfo.depthBiasEnable(false);
            rsCreateInfo.depthClampEnable(false);
            rsCreateInfo.frontFace(VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE);
            rsCreateInfo.lineWidth(1f); // in fragments
            rsCreateInfo.polygonMode(VK10.VK_POLYGON_MODE_FILL);
            rsCreateInfo.rasterizerDiscardEnable(false);

            VkPipelineShaderStageCreateInfo.Buffer stageCreateInfos
                    = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            // shader stage 0 - vertex shader:
            VkPipelineShaderStageCreateInfo vertCreateInfo
                    = stageCreateInfos.get(0);
            vertCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

            ByteBuffer entryPoint = stack.UTF8("main");

            long vertModuleHandle = shaderProgram.vertModuleHandle();
            vertCreateInfo.module(vertModuleHandle);
            vertCreateInfo.pName(entryPoint);
            vertCreateInfo.stage(VK10.VK_SHADER_STAGE_VERTEX_BIT);

            // shader stage 1 - fragment shader:
            VkPipelineShaderStageCreateInfo fragCreateInfo
                    = stageCreateInfos.get(1);
            fragCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO);

            long fragModuleHandle = shaderProgram.fragModuleHandle();
            fragCreateInfo.module(fragModuleHandle);
            fragCreateInfo.pName(entryPoint);
            fragCreateInfo.stage(VK10.VK_SHADER_STAGE_FRAGMENT_BIT);

            // vertex-input state - describes format of vertex data:
            VkPipelineVertexInputStateCreateInfo visCreateInfo
                    = VkPipelineVertexInputStateCreateInfo.calloc(stack);
            visCreateInfo.sType(
                    VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO
            );

            VkVertexInputBindingDescription.Buffer pBindingDesc
                    = mesh.generateBindingDescription(stack);
            visCreateInfo.pVertexBindingDescriptions(pBindingDesc);

            VkVertexInputAttributeDescription.Buffer pAttributeDesc
                    = mesh.generateAttributeDescriptions(stack);
            visCreateInfo.pVertexAttributeDescriptions(pAttributeDesc);

            // viewport location and dimensions:
            int framebufferHeight = framebufferExtent.height();
            int framebufferWidth = framebufferExtent.width();
            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.height(framebufferHeight);
            viewport.maxDepth(1f);
            viewport.minDepth(0f);
            viewport.width(framebufferWidth);
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

            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            long pipelineCache = VK10.VK_NULL_HANDLE; // disable cacheing
            VkAllocationCallbacks allocator = BaseApplication.allocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateGraphicsPipelines(logicalDevice,
                    pipelineCache, pCreateInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create graphics pipeline");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Create the descriptor-set pool for UBOs and samplers.
     *
     * @param poolSize the desired number of descriptors of each type
     * @return the handle of the new VkDescriptorPool
     */
    private static long createPool(int poolSize) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer pPoolSizes
                    = VkDescriptorPoolSize.calloc(2, stack);

            // The UBO descriptor pool will contain numImages descriptors:
            VkDescriptorPoolSize uboPool = pPoolSizes.get(0);
            uboPool.type(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            uboPool.descriptorCount(poolSize);

            // The sampler descriptor pool will contain numImages descriptors:
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

            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            VkAllocationCallbacks allocator = BaseApplication.allocator();
            LongBuffer pHandle = stack.mallocLong(1);
            int retCode = VK10.vkCreateDescriptorPool(
                    logicalDevice, createInfo, allocator, pHandle);
            Utils.checkForError(retCode, "create descriptor-set pool");
            long result = pHandle.get(0);

            return result;
        }
    }

    /**
     * Enumerate the images created by KHRSwapchain.
     *
     * @param chainHandle the handle of the VkSwapchainKHR
     * @return a new List of {@code VkImage} handles
     */
    private static List<Long> listImages(long chainHandle) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Count the images:
            VkDevice logicalDevice = BaseApplication.getLogicalDevice();
            IntBuffer pCount = stack.mallocInt(1);
            int retCode = KHRSwapchain.vkGetSwapchainImagesKHR(
                    logicalDevice, chainHandle, pCount, null);
            Utils.checkForError(retCode, "count swap-chain images");
            int numImages = pCount.get(0);

            // Enumerate the images:
            LongBuffer pHandles = stack.mallocLong(numImages);
            retCode = KHRSwapchain.vkGetSwapchainImagesKHR(
                    logicalDevice, chainHandle, pCount, pHandles);
            Utils.checkForError(retCode, "enumerate swap-chain images");

            // Collect the image handles into a list:
            List<Long> result = new ArrayList<>(numImages);
            for (int imageIndex = 0; imageIndex < numImages; ++imageIndex) {
                long imageHandle = pHandles.get(imageIndex);
                result.add(imageHandle);
            }

            return result;
        }
    }

    /**
     * Update the descriptor sets after a change.
     *
     * @param texture the texture to be used in rendering (not null)
     * @param samplerHandle the handle of the VkSampler for textures
     * @param ubos the uniform buffer objects (not null)
     * @param descriptorSetHandles the handles of the descriptor sets (not null,
     * unaffected)
     */
    private static void updateDescriptorSets(
            Texture texture, long samplerHandle,
            List<BufferResource> ubos, List<Long> descriptorSetHandles) {
        int numBytes = UniformValues.numBytes();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorBufferInfo.Buffer bufferInfo
                    = VkDescriptorBufferInfo.calloc(1, stack);
            bufferInfo.offset(0);
            bufferInfo.range(numBytes);

            VkDescriptorImageInfo.Buffer imageInfo
                    = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.imageLayout(
                    VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            long viewHandle = texture.viewHandle();
            imageInfo.imageView(viewHandle);
            imageInfo.sampler(samplerHandle);

            // Configure the descriptors in each set:
            VkWriteDescriptorSet.Buffer pWrites
                    = VkWriteDescriptorSet.calloc(2, stack);

            VkWriteDescriptorSet uboWrite = pWrites.get(0);
            uboWrite.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);
            uboWrite.descriptorCount(1);
            uboWrite.descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);

            uboWrite.dstArrayElement(0);
            uboWrite.dstBinding(0);
            uboWrite.pBufferInfo(bufferInfo);

            VkWriteDescriptorSet samplerWrite = pWrites.get(1);
            samplerWrite.sType(VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET);

            samplerWrite.descriptorCount(1);
            samplerWrite.descriptorType(
                    VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER);
            samplerWrite.dstArrayElement(0);
            samplerWrite.dstBinding(1);
            samplerWrite.pImageInfo(imageInfo);

            VkCopyDescriptorSet.Buffer pCopies = null;
            int numImages = ubos.size();
            for (int setIndex = 0; setIndex < numImages; ++setIndex) {
                long uboHandle = ubos.get(setIndex).handle();
                bufferInfo.buffer(uboHandle);

                long setHandle = descriptorSetHandles.get(setIndex);
                uboWrite.dstSet(setHandle);
                samplerWrite.dstSet(setHandle);

                VkDevice logicalDevice = BaseApplication.getLogicalDevice();
                VK10.vkUpdateDescriptorSets(logicalDevice, pWrites, pCopies);
            }
        }
    }
}
