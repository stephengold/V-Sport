/*
 Copyright (c) 2025-2026 Stephen Gold

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
package com.github.stephengold.vsport.physics;

import com.github.stephengold.vsport.input.InputManager;
import com.github.stephengold.vsport.input.InputProcessor;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An app to visualize 3-D physics using functional interfaces.
 *
 * @param <T> the type of PhysicsSpace to simulate
 */
public class FunctionalPhysicsApp<T extends PhysicsSpace>
        extends BasePhysicsApp<T>
        implements PhysicsTickListener {
    // *************************************************************************
    // fields

    /**
     * callback to calculate how much to advance the simulation before rendering
     * the next frame
     */
    private BiFunction<BasePhysicsApp, Float, Float> advanceAmount;
    /**
     * callback to initialize the graphics engine before the main update loop
     * begins
     */
    private Consumer<BasePhysicsApp> initialize;
    /**
     * callback to populate the PhysicsSpace during initialization
     */
    private Consumer<BasePhysicsApp> populateSpace;
    /**
     * callback invoked after each frame is rendered
     */
    private Consumer<BasePhysicsApp> postRender;
    /**
     * callback invoked before each frame is rendered
     */
    private Consumer<BasePhysicsApp> preRender;
    /**
     * callback to create the PhysicsSpace during initialization
     */
    private Function<BasePhysicsApp, T> createSpace;
    /**
     * callback invoked after each simulation step
     */
    private BiConsumer<BasePhysicsApp, Float> postPhysicsTick;
    /**
     * callback invoked before each simulation step
     */
    private BiConsumer<BasePhysicsApp, Float> prePhysicsTick;
    // *************************************************************************
    // constructors

    /**
     * Explicit no-arg constructor to avoid javadoc warnings from JDK 18+.
     */
    public FunctionalPhysicsApp() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Install a listener for keyboard input.
     *
     * @param function the listener to install (not {@code null})
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp addKeyboardListener(
            BiFunction<Integer, Boolean, Boolean> function) {
        InputProcessor processor = new InputProcessor() {
            @Override
            public void onKeyboard(int glfwKeyId, boolean isPressed) {
                boolean consumed = function.apply(glfwKeyId, isPressed);
                if (!consumed) {
                    super.onKeyboard(glfwKeyId, isPressed);
                }
            }
        };
        InputManager manager = getInputManager();
        manager.add(processor);

        return this;
    }

    /**
     * Replace the callback to calculate how much to advance the simulation
     * before rendering the next frame.
     *
     * @param function the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setAdvanceAmount(
            BiFunction<BasePhysicsApp, Float, Float> function) {
        this.advanceAmount = function;
        return this;
    }

    /**
     * Replace the function to create the PhysicsSpace during initialization.
     *
     * @param function the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setCreateSpace(
            Function<BasePhysicsApp, T> function) {
        this.createSpace = function;
        return this;
    }

    /**
     * Replace the function invoked to initialize the graphics engine before the
     * main update loop begins.
     *
     * @param consumer the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setInitialize(
            Consumer<BasePhysicsApp> consumer) {
        this.initialize = consumer;
        return this;
    }

    /**
     * Replace the function to populate the PhysicsSpace during initialization.
     *
     * @param consumer the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setPopulateSpace(
            Consumer<BasePhysicsApp> consumer) {
        this.populateSpace = consumer;
        return this;
    }

    /**
     * Replace the function to invoked after the simulation is stepped.
     *
     * @param consumer the function to use, or {@code null} for none. This used
     * to be a TriConsumer, but Clojure threw {@code ClassCastException} when it
     * tried to box the argument.
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setPostPhysicsTick(
            BiConsumer<BasePhysicsApp, Float> consumer) {
        this.postPhysicsTick = consumer;
        return this;
    }

    /**
     * Replace the function invoked after each frame is rendered.
     *
     * @param consumer the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setPostRender(
            Consumer<BasePhysicsApp> consumer) {
        this.postRender = consumer;
        return this;
    }

    /**
     * Replace the function to invoked before each simulation step.
     *
     * @param consumer the function to use, or {@code null} for none. This used
     * to be a TriConsumer, but Clojure threw {@code ClassCastException} when it
     * tried to box the argument.
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setPrePhysicsTick(
            BiConsumer<BasePhysicsApp, Float> consumer) {
        this.prePhysicsTick = consumer;
        return this;
    }

    /**
     * Replace the function invoked before each frame is rendered.
     *
     * @param consumer the function to use, or {@code null} for none
     * @return the modified application, for chaining
     */
    public FunctionalPhysicsApp setPreRender(
            Consumer<BasePhysicsApp> consumer) {
        this.preRender = consumer;
        return this;
    }
    // *************************************************************************
    // BasePhysicsApp methods

    /**
     * Create the PhysicsSpace during initialization.
     *
     * @return a new object
     */
    @Override
    final public T createSpace() {
        T result = createSpace.apply(this);

        // Always add this object as a tick listener:
        result.addTickListener(this);

        return result;
    }

    /**
     * Callback invoked before the main update loop begins.
     */
    @Override
    final public void initialize() {
        super.initialize();
        if (initialize != null) {
            initialize.accept(this);
        }
    }

    /**
     * Callback to populate the PhysicsSpace.
     */
    @Override
    final public void populateSpace() {
        if (populateSpace != null) {
            populateSpace.accept(this);
        }
    }

    /**
     * Callback invoked during each iteration of the render loop.
     */
    @Override
    final public void render() {
        if (preRender != null) {
            preRender.accept(this);
        }
        super.render();
        if (postRender != null) {
            postRender.accept(this);
        }
    }

    /**
     * Advance the physics simulation by the specified amount. Invoked during
     * each update.
     *
     * @param wallClockSeconds the elapsed wall-clock time since the previous
     * invocation of {@code updatePhysics} (in seconds, &ge;0)
     */
    @Override
    final public void updatePhysics(float wallClockSeconds) {
        if (advanceAmount == null) {
            super.updatePhysics(wallClockSeconds);

        } else {
            Object obj = advanceAmount.apply(this, wallClockSeconds);
            float simulateSeconds;
            if (obj instanceof Double) {
                double doubleSeconds = (Double) obj;
                simulateSeconds = (float) doubleSeconds;
            } else {
                simulateSeconds = (Float) obj;
            }
            super.updatePhysics(simulateSeconds);
        }
    }
    // *************************************************************************
    // PhysicsTickListener methods

    /**
     * Callback invoked after the PhysicsSpace has been stepped.
     *
     * @param space the space that was just stepped (not {@code null})
     * @param timeStep the duration of the simulation step (in seconds, &ge;0)
     */
    @Override
    final public void physicsTick(PhysicsSpace space, float timeStep) {
        if (postPhysicsTick != null) {
            postPhysicsTick.accept(this, timeStep);
        }
    }

    /**
     * Callback invoked before the PhysicsSpace is stepped.
     *
     * @param space the space that's about to be stepped (not {@code null})
     * @param timeStep the duration of the simulation step (in seconds, &ge;0)
     */
    @Override
    final public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        if (prePhysicsTick != null) {
            prePhysicsTick.accept(this, timeStep);
        }
    }
}
