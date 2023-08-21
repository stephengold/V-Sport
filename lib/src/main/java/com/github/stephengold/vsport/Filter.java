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

import org.lwjgl.vulkan.VK10;

/**
 * Enumerate options for pixel/texel filtering such as magnification or
 * minification. Corresponds to the native types {@code VkFilter} and
 * {@code VkSamplerMipmapMode}.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public enum Filter {
    // *************************************************************************
    // values

    /**
     * interpolate linearly between adjacent samples, ignore MIP mapping
     */
    Linear(VK10.VK_FILTER_LINEAR, VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST, 0f),
    /**
     * interpolate linearly between adjacent samples and MIP-map levels
     */
    LinearMipmapLinear(
            VK10.VK_FILTER_LINEAR, VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR, 31f),
    /**
     * interpolate linearly between adjacent samples in the nearest MIP-map
     * level
     */
    LinearMipmapNearest(
            VK10.VK_FILTER_LINEAR, VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST, 31f),
    /**
     * use the sample nearest to the texture coordinate, ignore MIP mapping
     */
    Nearest(VK10.VK_FILTER_NEAREST, VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST, 0f),
    /**
     * use the sample nearest to the texture coordinate, interpolate linearly
     * between MIP-map levels
     */
    NearestMipmapLinear(
            VK10.VK_FILTER_NEAREST, VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR, 31f),
    /**
     * use the sample nearest to the texture coordinate in the nearest MIP-map
     * level
     */
    NearestMipmapNearest(
            VK10.VK_FILTER_NEAREST, VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST, 31f);
    // *************************************************************************
    // fields

    /**
     * the highest MIP-map level to use during minification (&ge;0, &le;31,
     * 0&rarr;ignore MIP maps)
     */
    final private float maxLod;
    /**
     * {@code VkFilter} code used when creating a texture sampler or a blit
     * command
     */
    final private int filterCode;
    /**
     * {@code VkSamplerMipmapMode} code used when creating a texture sampler
     */
    final private int mipmapMode;
    // *************************************************************************
    // constructors

    /**
     * Construct an enum value.
     *
     * @param filterCode the corresponding {@code VkFilter} code
     * @param mipmapMode the corresponding {@code VkSamplerMipmapMode} code
     * @param maxLod the highest MIP-map level to use during minification
     * (&ge;0, &le;31, 0&rarr;ignore MIP maps)
     */
    Filter(int filterCode, int mipmapMode, float maxLod) {
        this.filterCode = filterCode;
        this.mipmapMode = mipmapMode;
        this.maxLod = maxLod;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Return the {@code VkFilter} encoding.
     *
     * @return the encoded value
     */
    int filterCode() {
        return filterCode;
    }

    /**
     * Test whether the value is valid for magnification.
     *
     * @return true if valid, otherwise false
     */
    boolean isValidForMagnification() {
        if (maxLod == 0f) {
            return true;
        } else { // magnification should never use the MIP maps
            return false;
        }
    }

    /**
     * Return the highest MIP-map level to use during minification.
     *
     * @return the highest level to use (&ge;0, &le;31, 0&rarr;ignore MIP maps)
     */
    float maxLod() {
        assert maxLod >= 0f : maxLod;
        assert maxLod <= 31f : maxLod;
        return maxLod;
    }

    /**
     * Return the {@code VkSamplerMipmapMode} encoding used when creating a
     * texture sampler.
     *
     * @return the encoded value
     */
    int mipmapMode() {
        return mipmapMode;
    }
}
