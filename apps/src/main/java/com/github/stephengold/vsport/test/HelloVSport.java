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
package com.github.stephengold.vsport.test;

import com.github.stephengold.vsport.AssimpUtils;
import com.github.stephengold.vsport.BaseApplication;
import com.github.stephengold.vsport.FlipAxes;
import com.github.stephengold.vsport.Geometry;
import com.github.stephengold.vsport.Mesh;
import com.github.stephengold.vsport.TextureKey;
import com.github.stephengold.vsport.Vertex;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.assimp.Assimp;

/**
 * The first tutorial app for the V-Sport graphics engine.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class HelloVSport extends BaseApplication {
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the HelloVSport application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        HelloVSport application = new HelloVSport();
        application.start();
    }
    // *************************************************************************
    // BaseApplication methods

    /**
     * Callback invoked after the main update loop terminates.
     */
    @Override
    public void cleanUp() {
    }

    /**
     * Initialize this application.
     */
    @Override
    public void initialize() {
        String modelName = "/Models/viking_room/viking_room.obj";
        int postFlags = Assimp.aiProcess_DropNormals | Assimp.aiProcess_FlipUVs;
        List<Integer> indices = null;
        List<Vertex> vertices = new ArrayList<>();
        AssimpUtils.extractTriangles(modelName, postFlags, indices, vertices);
        Mesh mesh = Mesh.newInstance(vertices);

        TextureKey textureKey = new TextureKey(
                "classpath:/Models/viking_room/viking_room.png",
                true, FlipAxes.noFlip);

        Geometry geometry = new Geometry(mesh);
        geometry.setProgram("Unshaded/Texture");
        geometry.setTexture(textureKey);
    }

    /**
     * Callback invoked during each iteration of the main update loop.
     */
    @Override
    public void render() {
        super.render();
    }
}
