/*
 Copyright (c) 2023-2024, Stephen Gold

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

import com.github.stephengold.vsport.BaseApplication;
import com.github.stephengold.vsport.Geometry;
import com.github.stephengold.vsport.Mesh;
import com.github.stephengold.vsport.TextureKey;
import com.github.stephengold.vsport.Topology;
import com.github.stephengold.vsport.Vertex;
import com.github.stephengold.vsport.importers.AssimpUtils;
import com.github.stephengold.vsport.input.CameraInputProcessor;
import com.github.stephengold.vsport.input.RotateMode;
import com.github.stephengold.vsport.mesh.OctasphereMesh;
import com.github.stephengold.vsport.mesh.RectangleMesh;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.assimp.Assimp;

/**
 * The first tutorial app for the V-Sport graphics engine.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class HelloVSport extends BaseApplication {
    // *************************************************************************
    // constants

    /**
     * rotation matrix to transform Z-up coordinates to Y-up coordinates
     */
    final private static Matrix3fc zupToYup = new Matrix3f(
            0f, 1f, 0f,
            0f, 0f, 1f,
            1f, 0f, 0f).transpose();
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
    // constructors

    /**
     * A no-arg constructor to avoid javadoc warnings from JDK 18.
     */
    public HelloVSport() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the HelloVSport application.
     *
     * @param arguments the array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        HelloVSport application = new HelloVSport();
        application.start();
    }
    // *************************************************************************
    // BaseApplication methods

    /**
     * Callback invoked by V-Sport after the main update loop terminates.
     */
    @Override
    public void cleanUp() {
        // do nothing
    }

    /**
     * Initialize the application.
     */
    @Override
    public void initialize() {
        startTime = System.nanoTime();

        // meshes:
        int numRefineSteps = 5;
        Mesh globeMesh = new OctasphereMesh(numRefineSteps);
        Vector4fc uCoefficients = new Vector4f(0.5f, 0f, 0f, 0.5f);
        Vector4fc vCoefficients = new Vector4f(0f, 1f, 0f, 0f);
        globeMesh.transformUvs(uCoefficients, vCoefficients);

        String modelName = "/Models/viking_room/viking_room.obj";
        int postFlags = Assimp.aiProcess_FlipUVs;
        List<Integer> indices = null;
        List<Vertex> vertices = new ArrayList<>();
        AssimpUtils.extractTriangles(modelName, postFlags, indices, vertices);
        for (Vertex v : vertices) {
            v.rotate(zupToYup);
        }
        Mesh roomMesh = Mesh.newInstance(Topology.TriangleList, vertices);

        Mesh squareMesh = new RectangleMesh();

        // texture keys:
        TextureKey marsKey
                = new TextureKey("classpath:/Textures/sss/2k_mars.jpg");
        TextureKey roomKey = new TextureKey(
                "classpath:/Models/viking_room/viking_room.png");
        TextureKey photoKey
                = new TextureKey("classpath:/Textures/texture.jpg");

        // geometries:
        mars = new Geometry(globeMesh);
        mars.setProgram("Phong/Distant/Texture");
        mars.setTexture(marsKey);
        mars.scale(0.4f);
        mars.setLocation(0f, 0.2f, 1f);

        Geometry photo = new Geometry(squareMesh);
        photo.setOrientation(new Matrix3f(
                0f, -1f, 0f,
                0f, 0f, 1f,
                -1f, 0f, 0f).transpose());
        photo.setProgram("Unshaded/Texture");
        photo.setTexture(photoKey);
        photo.setLocation(1.4f, -0.2f, 1f);

        Geometry room = new Geometry(roomMesh);
        room.setBackCulling(false);
        room.setProgram("Unshaded/Texture");
        room.setTexture(roomKey);

        // Configure the camera:
        getProjection().setZClip(0.1f, 10f);

        Vector3f eye = new Vector3f(2f, 2f, 2f);
        Vector3f target = new Vector3f(0f, 0f, 0f);
        getCamera().reposition(eye, target);

        CameraInputProcessor cip = getCameraInputProcessor();
        cip.setMoveSpeed(2f);
        cip.setRotationMode(RotateMode.DragLMB);
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
            mars.rotate(zupToYup);
        }

        super.render();
    }
}
