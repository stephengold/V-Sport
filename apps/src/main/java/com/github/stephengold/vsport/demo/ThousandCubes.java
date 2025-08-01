/*
 Copyright (c) 2022-2025 Stephen Gold

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
package com.github.stephengold.vsport.demo;

import com.github.stephengold.vsport.Constants;
import com.github.stephengold.vsport.Geometry;
import com.github.stephengold.vsport.input.InputProcessor;
import com.github.stephengold.vsport.input.RotateMode;
import com.github.stephengold.vsport.mesh.CrosshairsMesh;
import com.github.stephengold.vsport.mesh.LoopMesh;
import com.github.stephengold.vsport.physics.BasePhysicsApp;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.PlaneCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.FastMath;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

/**
 * Drop 1000 cubes onto a horizontal surface and launch balls at them (graphical
 * demo).
 * <p>
 * Derived from ThousandCubes.java by Yanis Boudiaf.
 */
public class ThousandCubes extends BasePhysicsApp<PhysicsSpace> {
    // *************************************************************************
    // constants

    /**
     * simulation speed when "paused"
     */
    final private static float PAUSED_SPEED = 1e-9f;
    // *************************************************************************
    // fields

    /**
     * shape for stacked boxes
     */
    private static BoxCollisionShape boxShape;
    /**
     * shape for bodies launched when the E key is pressed
     */
    private static CollisionShape launchShape;
    /**
     * simulation speed (simulated seconds per wall-clock second)
     */
    private static float physicsSpeed = 1f;
    /**
     * cross geometry for the crosshairs
     */
    private static Geometry cross;
    /**
     * loop geometry for the crosshairs
     */
    private static Geometry loop;
    /**
     * generate random colors
     */
    final private static Random random = new Random();
    /**
     * temporary storage for location vectors
     */
    final private static Vector3f tmpLocation = new Vector3f();
    // *************************************************************************
    // constructors

    /**
     * Instantiate the ThousandCubes application.
     * <p>
     * This no-arg constructor was made explicit to avoid javadoc warnings from
     * JDK 18+.
     */
    public ThousandCubes() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the ThousandCubes application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        Logger.getLogger("").setLevel(Level.WARNING);
        new ThousandCubes().start();
    }
    // *************************************************************************
    // BasePhysicsApp methods

    /**
     * Create the PhysicsSpace. Invoked once during initialization.
     *
     * @return a new instance
     */
    @Override
    public PhysicsSpace createSpace() {
        return new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
    }

    /**
     * Initialize the application. Invoked once.
     */
    @Override
    public void initialize() {
        super.initialize();

        addCrosshairs();
        configureCamera();
        configureInput();
        setBackgroundColor(Constants.SKY_BLUE);
    }

    /**
     * Populate the PhysicsSpace. Invoked once during initialization.
     */
    @Override
    public void populateSpace() {
        boxShape = new BoxCollisionShape(0.5f);
        launchShape = new SphereCollisionShape(0.5f);

        CollisionShape planeShape
                = new PlaneCollisionShape(new Plane(Vector3f.UNIT_Y, 0f));
        PhysicsRigidBody floor
                = new PhysicsRigidBody(planeShape, PhysicsBody.massForStatic);
        physicsSpace.addCollisionObject(floor);
        visualizeShape(floor, 0.05f);

        for (int i = 0; i < 10; ++i) {
            for (int j = 0; j < 10; ++j) {
                for (int k = 0; k < 10; ++k) {
                    addBox(2f * i, 2f * j, 2f * k - 2.5f);
                }
            }
        }
    }

    /**
     * Callback invoked during each iteration of the main update loop.
     */
    @Override
    public void render() {
        updateScale();
        super.render();
    }

    /**
     * Advance the physics simulation by the specified amount. Invoked during
     * each update.
     *
     * @param wallClockSeconds the elapsed wall-clock time since the previous
     * invocation of {@code updatePhysics} (in seconds, &ge;0)
     */
    @Override
    public void updatePhysics(float wallClockSeconds) {
        float simulateSeconds = physicsSpeed * wallClockSeconds;
        physicsSpace.update(simulateSeconds);
    }
    // *************************************************************************
    // private methods

    /**
     * Add a dynamic box to the space, at the specified coordinates.
     *
     * @param x the desired X coordinate (in physics space)
     * @param y the desired Y coordinate (in physics space)
     * @param z the desired Z coordinate (in physics space)
     */
    private void addBox(float x, float y, float z) {
        float mass = 10f;
        PhysicsRigidBody box = new PhysicsRigidBody(boxShape, mass);
        physicsSpace.addCollisionObject(box);

        box.setAngularDamping(0.1f);
        box.setLinearDamping(0.3f);
        tmpLocation.set(x, y, z);
        box.setPhysicsLocation(tmpLocation);

        float red = FastMath.pow(random.nextFloat(), 2.2f);
        float green = FastMath.pow(random.nextFloat(), 2.2f);
        float blue = FastMath.pow(random.nextFloat(), 2.2f);
        visualizeShape(box).setColor(new Vector4f(red, green, blue, 1f));
    }

    private static void addCrosshairs() {
        float crossWidth = 0.1f;
        cross = new Geometry(new CrosshairsMesh(crossWidth, crossWidth))
                .setColor(Constants.YELLOW)
                .setProgram("Unshaded/Clipspace/Monochrome");
        loop = new Geometry(
                new LoopMesh(32, 0.3f * crossWidth, 0.3f * crossWidth))
                .setColor(Constants.YELLOW)
                .setProgram("Unshaded/Clipspace/Monochrome");
    }

    /**
     * Configure the Camera and CIP during startup.
     */
    private static void configureCamera() {
        getCameraInputProcessor().setRotationMode(RotateMode.Immediate);
        cam.setLocation(60f, 15f, 28f)
                .setAzimuth(-2.7f)
                .setUpAngle(-0.25f);
    }

    /**
     * Configure keyboard input during startup.
     */
    private void configureInput() {
        getInputManager().add(new InputProcessor() {
            @Override
            public void onKeyboard(int keyId, boolean isPressed) {
                switch (keyId) {
                    case GLFW.GLFW_KEY_E:
                        if (isPressed) {
                            launchRedBall();
                        }
                        return;

                    case GLFW.GLFW_KEY_PAUSE:
                    case GLFW.GLFW_KEY_PERIOD:
                        if (isPressed) {
                            togglePause();
                        }
                        return;

                    default:
                }
                super.onKeyboard(keyId, isPressed);
            }
        });
    }

    private void launchRedBall() {
        float mass = 10f;
        PhysicsRigidBody missile = new PhysicsRigidBody(launchShape, mass);
        physicsSpace.addCollisionObject(missile);

        float radius = launchShape.maxRadius();
        missile.setCcdMotionThreshold(radius);
        missile.setCcdSweptSphereRadius(radius);

        float speed = 100f;
        Vector3f velocity = cam.getDirection().mult(speed);
        missile.setLinearVelocity(velocity);

        missile.setAngularDamping(0.1f);
        missile.setLinearDamping(0.3f);
        missile.setPhysicsLocation(cam.getLocation());

        visualizeShape(missile).setColor(Constants.RED);
    }

    private static void togglePause() {
        physicsSpeed = (physicsSpeed <= PAUSED_SPEED) ? 1f : PAUSED_SPEED;
    }

    /**
     * Scale the crosshair geometries so they will render as an equal-armed
     * cross and a circle, regardless of the window's aspect ratio.
     */
    private static void updateScale() {
        float aspectRatio = aspectRatio();
        float yScale = Math.min(1f, aspectRatio);
        float xScale = yScale / aspectRatio;
        Vector3f newScale = new Vector3f(xScale, yScale, 1f);

        cross.setScale(newScale);
        loop.setScale(newScale);
    }
}
