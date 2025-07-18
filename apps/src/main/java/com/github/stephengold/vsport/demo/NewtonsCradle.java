/*
 Copyright (c) 2020-2025 Stephen Gold

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
import com.github.stephengold.vsport.input.CameraInputProcessor;
import com.github.stephengold.vsport.input.InputProcessor;
import com.github.stephengold.vsport.input.RotateMode;
import com.github.stephengold.vsport.physics.BasePhysicsApp;
import com.github.stephengold.vsport.physics.ConstraintGeometry;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.joints.JointEnd;
import com.jme3.bullet.joints.Point2PointJoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import java.util.logging.Level;
import java.util.logging.Logger;
import jme3utilities.math.MyMath;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.glfw.GLFW;

/**
 * A physics demo that simulates a Newton's cradle.
 * <p>
 * https://en.wikipedia.org/wiki/Newton%27s_cradle
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class NewtonsCradle extends BasePhysicsApp<PhysicsSpace> {
    // *************************************************************************
    // constants

    /**
     * simulation speed when "paused"
     */
    final private static float PAUSED_SPEED = 1e-9f;
    /**
     * color to visualize the balls
     */
    final private static Vector4fc BALL_COLOR
            = new Vector4f(0.01f, 0.01f, 0.01f, 1f);
    // *************************************************************************
    // fields

    /**
     * simulation speed (simulated seconds per wall-clock second)
     */
    private static float physicsSpeed = 1f;
    // *************************************************************************
    // constructors

    /**
     * Instantiate the NewtonsCradle application.
     * <p>
     * This no-arg constructor was made explicit to avoid javadoc warnings from
     * JDK 18+.
     */
    public NewtonsCradle() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the NewtonsCradle application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        Logger.getLogger("").setLevel(Level.WARNING);
        NewtonsCradle application = new NewtonsCradle();
        application.start();
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
        PhysicsSpace result
                = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        result.setAccuracy(0.01f);
        result.setGravity(new Vector3f(0f, -150f, 0f));

        return result;
    }

    /**
     * Initialize the application. Invoked once.
     */
    @Override
    public void initialize() {
        super.initialize();

        configureCamera();
        configureInput();
        setBackgroundColor(Constants.SKY_BLUE);
    }

    /**
     * Populate the PhysicsSpace. Invoked once during initialization.
     */
    @Override
    public void populateSpace() {
        restartSimulation(5);
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
     * Add a dynamic ball to the space, suspended between 2 single-ended
     * point-to-point joints.
     *
     * @param xOffset the desired location on the X axis
     * @return a new instance
     */
    private PhysicsRigidBody addSuspendedBall(float xOffset) {
        float radius = 9.9f;
        SphereCollisionShape sphere = new SphereCollisionShape(radius);
        PhysicsRigidBody result = new PhysicsRigidBody(sphere);
        result.setFriction(0f);
        result.setPhysicsLocation(new Vector3f(xOffset, 0f, 0f));
        result.setRestitution(1f);
        physicsSpace.addCollisionObject(result);

        visualizeShape(result).setColor(BALL_COLOR);

        float wireLength = 80f;
        float yOffset = wireLength * MyMath.rootHalf;

        Vector3f offset = new Vector3f(0f, yOffset, +yOffset);
        Point2PointJoint joint1 = new Point2PointJoint(result, offset);
        physicsSpace.addJoint(joint1);
        new ConstraintGeometry(joint1, JointEnd.A);

        offset.set(0f, yOffset, -yOffset);
        Point2PointJoint joint2 = new Point2PointJoint(result, offset);
        physicsSpace.addJoint(joint2);
        new ConstraintGeometry(joint2, JointEnd.A);

        return result;
    }

    /**
     * Configure the Camera and CIP during startup.
     */
    private static void configureCamera() {
        CameraInputProcessor cip = getCameraInputProcessor();
        cip.setMoveSpeed(30f);
        cip.setRotationMode(RotateMode.DragLMB);

        cam.setAzimuth(-2f)
                .setLocation(72f, 35f, 140f)
                .setUpAngle(-0.2f);
    }

    /**
     * Configure keyboard input during startup.
     */
    private void configureInput() {
        getInputManager().add(new InputProcessor() {
            @Override
            public void onKeyboard(int keyId, boolean isPressed) {
                switch (keyId) {
                    case GLFW.GLFW_KEY_1:
                    case GLFW.GLFW_KEY_F1:
                    case GLFW.GLFW_KEY_KP_1:
                        if (isPressed) {
                            restartSimulation(1);
                        }
                        return;

                    case GLFW.GLFW_KEY_2:
                    case GLFW.GLFW_KEY_F2:
                    case GLFW.GLFW_KEY_KP_2:
                        if (isPressed) {
                            restartSimulation(2);
                        }
                        return;

                    case GLFW.GLFW_KEY_3:
                    case GLFW.GLFW_KEY_F3:
                    case GLFW.GLFW_KEY_KP_3:
                        if (isPressed) {
                            restartSimulation(3);
                        }
                        return;

                    case GLFW.GLFW_KEY_4:
                    case GLFW.GLFW_KEY_F4:
                    case GLFW.GLFW_KEY_KP_4:
                        if (isPressed) {
                            restartSimulation(4);
                        }
                        return;

                    case GLFW.GLFW_KEY_5:
                    case GLFW.GLFW_KEY_F5:
                    case GLFW.GLFW_KEY_KP_5:
                        if (isPressed) {
                            restartSimulation(5);
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

    /**
     * Restart the simulation (paused) with the specified number of balls.
     *
     * @param numBalls (&ge;1)
     */
    private void restartSimulation(int numBalls) {
        physicsSpace.destroy();
        physicsSpeed = PAUSED_SPEED;

        float xSeparation = 20f;

        // center-to-center separation between the first and last balls
        float xExtent = (numBalls - 1) * xSeparation;

        float x0 = -xExtent / 2;
        PhysicsRigidBody[] balls = new PhysicsRigidBody[numBalls];
        for (int ballIndex = 0; ballIndex < numBalls; ++ballIndex) {
            float x = x0 + ballIndex * xSeparation;
            balls[ballIndex] = addSuspendedBall(x);
        }

        Vector3f kick = new Vector3f(-20f * numBalls, 0f, 0f);
        balls[0].applyCentralImpulse(kick);
    }

    private static void togglePause() {
        physicsSpeed = (physicsSpeed <= PAUSED_SPEED) ? 1f : PAUSED_SPEED;
    }
}
