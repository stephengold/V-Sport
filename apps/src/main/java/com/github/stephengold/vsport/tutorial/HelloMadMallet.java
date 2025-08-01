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
package com.github.stephengold.vsport.tutorial;

import com.github.stephengold.vsport.input.RotateMode;
import com.github.stephengold.vsport.physics.BasePhysicsApp;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.objects.PhysicsBody;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

/**
 * A simple example of a dynamic rigid body with an implausible center.
 * <p>
 * Builds upon HelloStaticBody.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class HelloMadMallet extends BasePhysicsApp<PhysicsSpace> {
    // *************************************************************************
    // constructors

    /**
     * Instantiate the HelloMadMallet application.
     * <p>
     * This no-arg constructor was made explicit to avoid javadoc warnings from
     * JDK 18+.
     */
    public HelloMadMallet() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Main entry point for the HelloMadMallet application.
     *
     * @param arguments array of command-line arguments (not null)
     */
    public static void main(String[] arguments) {
        HelloMadMallet application = new HelloMadMallet();
        application.start();
    }
    // *************************************************************************
    // BasePhysicsApp methods

    /**
     * Create the PhysicsSpace. Invoked once during initialization.
     *
     * @return a new object
     */
    @Override
    public PhysicsSpace createSpace() {
        PhysicsSpace result
                = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        result.setGravity(new Vector3f(0f, -50f, 0f));

        return result;
    }

    /**
     * Initialize the application. Invoked once.
     */
    @Override
    public void initialize() {
        super.initialize();
        getCameraInputProcessor().setRotationMode(RotateMode.DragLMB);

        // Position the camera for a good view.
        cam.setAzimuth(-3.05f)
                .setLocation(10f, -2.75f, 0f)
                .setUpAngle(0.05f);
    }

    /**
     * Populate the PhysicsSpace. Invoked once during initialization.
     */
    @Override
    public void populateSpace() {
        // Construct a compound shape for the mallet.
        float headLength = 1f;
        float headRadius = 0.5f;
        Vector3f hes = new Vector3f(headLength / 2f, headRadius, headRadius);
        CollisionShape headShape
                = new CylinderCollisionShape(hes, PhysicsSpace.AXIS_X);

        float handleLength = 3f;
        float handleRadius = 0.3f;
        hes.set(handleRadius, handleRadius, handleLength / 2f);
        CollisionShape handleShape
                = new CylinderCollisionShape(hes, PhysicsSpace.AXIS_Z);

        CompoundCollisionShape malletShape = new CompoundCollisionShape();
        malletShape.addChildShape(handleShape, 0f, 0f, handleLength / 2f);
        malletShape.addChildShape(headShape, 0f, 0f, handleLength);

        // Create a dynamic body for the mallet.
        float mass = 2f;
        PhysicsRigidBody mallet = new PhysicsRigidBody(malletShape, mass);
        mallet.setPhysicsLocation(new Vector3f(0f, 4f, 0f));

        // Increase the mallet's angular damping to stabilize it.
        mallet.setAngularDamping(0.9f);

        physicsSpace.addCollisionObject(mallet);

        // Create a static disc and add it to the space.
        float discRadius = 5f;
        float discThickness = 0.5f;
        CollisionShape discShape = new CylinderCollisionShape(
                discRadius, discThickness, PhysicsSpace.AXIS_Y);
        PhysicsRigidBody disc
                = new PhysicsRigidBody(discShape, PhysicsBody.massForStatic);
        physicsSpace.addCollisionObject(disc);
        disc.setPhysicsLocation(new Vector3f(0f, -3f, 0f));

        // Visualize the mallet, including its local axes.
        visualizeShape(mallet);
        float debugAxisLength = 1f;
        visualizeAxes(mallet, debugAxisLength);

        // Visualize the shape of the disc:
        visualizeShape(disc);
    }
}
