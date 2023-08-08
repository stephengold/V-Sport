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
import com.github.stephengold.vsport.mesh.OctasphereMesh;
import com.github.stephengold.vsport.mesh.RectangleMesh;
import com.jme3.math.FastMath;
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
    // fields

    /**
     * spinning globe
     */
    private static Geometry mars;
    /**
     * time when the application started
     */
    private static long startTime;
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
        startTime = System.nanoTime();

        // meshes:
        int numRefineSteps = 5;
        Mesh globeMesh = OctasphereMesh.getMesh(numRefineSteps);

        String modelName = "/Models/viking_room/viking_room.obj";
        int postFlags = Assimp.aiProcess_DropNormals | Assimp.aiProcess_FlipUVs;
        List<Integer> indices = null;
        List<Vertex> vertices = new ArrayList<>();
        AssimpUtils.extractTriangles(modelName, postFlags, indices, vertices);
        Mesh roomMesh = Mesh.newInstance(vertices);

        Mesh squareMesh = new RectangleMesh();
        squareMesh.dropNormals(); // TODO make this unnecessary

        // texture keys:
        TextureKey marsKey
                = new TextureKey("classpath:/Textures/sss/2k_mars.jpg");
        TextureKey roomKey = new TextureKey(
                "classpath:/Models/viking_room/viking_room.png",
                true, FlipAxes.noFlip);
        TextureKey photoKey
                = new TextureKey("classpath:/Textures/texture.jpg");

        // geometries:
        mars = new Geometry(globeMesh);
        mars.setProgram("Unshaded/Texture");
        mars.setTexture(marsKey);
        mars.scale(0.4f);
        mars.setTranslation(1f, 0f, 0.2f);

        Geometry photo = new Geometry(squareMesh);
        photo.setProgram("Unshaded/Texture");
        photo.setTexture(photoKey);
        photo.setOrientation(FastMath.PI, 0f, 0f, 1f);
        photo.setTranslation(1f, 1.4f, -0.2f);

        Geometry room = new Geometry(roomMesh);
        room.setProgram("Unshaded/Texture");
        room.setTexture(roomKey);
    }

    /**
     * Callback invoked during each iteration of the main update loop.
     */
    @Override
    public void render() {
        if (mars != null) {
            long nanoseconds = System.nanoTime() - startTime;
            float rotationAngle = 2e-10f * nanoseconds; // in radians
            mars.setOrientation(rotationAngle, 0f, 0f, 1f);
        }

        super.render();
    }
}
