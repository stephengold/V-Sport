/*
 Copyright (c) 2023, Stephen Gold

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

import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

/**
 * Utility methods to compile GLSL shaders using SPIR-V.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from ShaderSPIRVUtils.java in Cristian Herrera's Vulkan-Tutorial-Java
 * project.
 */
final class SpirvUtils {
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private SpirvUtils() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Compile an SPIR-V shader program from source code stored in the specified
     * classpath resource.
     *
     * @param resourceName the name of the classpath resource containing source
     * code (not null)
     * @param shaderKind the kind of shader
     * @return a handle of the compiled SPIR-V code
     */
    static long compileShaderFromClasspath(
            String resourceName, int shaderKind) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create shader compiler");
        }

        String sourceCode = Utils.loadResourceAsString(resourceName);
        long additionalOptions = MemoryUtil.NULL;
        long result = Shaderc.shaderc_compile_into_spv(compiler, sourceCode,
                shaderKind, resourceName, "main", additionalOptions);
        if (result == MemoryUtil.NULL) {
            throw new RuntimeException(
                    "Failed to compile shader " + resourceName);
        }
        int retCode = Shaderc.shaderc_result_get_compilation_status(result);
        if (retCode != Shaderc.shaderc_compilation_status_success) {
            String msg = Shaderc.shaderc_result_get_error_message(result);
            throw new RuntimeException(
                    "Failed to compile shader " + resourceName + ":\n " + msg);
        }

        Shaderc.shaderc_compiler_release(compiler); // TODO never release?

        return result;
    }
}
