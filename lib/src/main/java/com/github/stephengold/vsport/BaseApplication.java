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

import com.github.stephengold.vsport.input.CameraInputProcessor;
import com.github.stephengold.vsport.input.InputManager;
import com.github.stephengold.vsport.input.InputProcessor;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.joml.Vector4fc;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;

/**
 * A single-window, 3-D visualization application using LWJGL v3, GLFW, and
 * Vulkan.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from Cristian Herrera's Vulkan-Tutorial-Java project.
 */
abstract public class BaseApplication {
    // *************************************************************************
    // constants

    /**
     * version of the V-Sport graphics engine
     */
    final static int engineVersion = VK10.VK_MAKE_VERSION(0, 1, 0);
    /**
     * timeout period for synchronization (in nanoseconds)
     */
    final public static long noTimeout = 0xFFFFFFFFFFFFFFFFL;
    /**
     * name of the graphics engine
     */
    final public static String engineName = "V-Sport";
    // *************************************************************************
    // fields

    /**
     * process user input for the camera
     */
    private static CameraInputProcessor cameraInputProcessor;
    /**
     * all visible geometries
     */
    private static final Collection<Geometry> visibleGeometries
            = new HashSet<>(256);
    /**
     * convenient access to user input
     */
    private static InputManager inputManager;
    /**
     * GLFW handle of the window used to render geometries
     */
    private static long windowHandle = VK10.VK_NULL_HANDLE;
    /**
     * map program names to programs
     */
    final private static Map<String, ShaderProgram> programMap
            = new HashMap<>(16);
    /**
     * map texture keys to cached textures
     */
    final private static Map<TextureKey, Texture> textureMap
            = new HashMap<>(16);
    // *************************************************************************
    // new methods exposed

    /**
     * Return the aspect ratio of the displayed frame buffer.
     *
     * @return the width divided by the height (&gt;0)
     */
    public static float aspectRatio() {
        int width = Internals.framebufferWidth();
        int height = Internals.framebufferHeight();
        float ratio = width / (float) height;

        assert ratio > 0f : ratio;
        return ratio;
    }

    /**
     * Access the current camera for rendering.
     *
     * @return the pre-existing instance (not null)
     */
    public static Camera getCamera() {
        GlobalUniformValues guv = Internals.getGlobalUniformValues();
        Camera result = guv.getCamera();

        assert result != null;
        return result;
    }

    /**
     * Access the camera's input processor.
     *
     * @return the pre-existing instance (not null)
     */
    public static CameraInputProcessor getCameraInputProcessor() {
        assert cameraInputProcessor != null;
        return cameraInputProcessor;
    }

    /**
     * Access the input manager.
     *
     * @return the pre-existing instance (not null)
     */
    public static InputManager getInputManager() {
        assert inputManager != null;
        return inputManager;
    }

    /**
     * Access the named ShaderProgram, returning a cached result if possible.
     *
     * @param name (not null)
     * @return a valid program (not null)
     */
    static ShaderProgram getProgram(String name) {
        if (!programMap.containsKey(name)) {
            ShaderProgram program = new ShaderProgram(name);
            programMap.put(name, program);
        }

        ShaderProgram result = programMap.get(name);
        assert result != null;
        return result;
    }

    /**
     * Return the Texture for the specified key.
     *
     * @param key (not null)
     * @return a valid texture (not null)
     */
    static Texture getTexture(TextureKey key) {
        if (!textureMap.containsKey(key)) {
            Texture texture = key.load();
            textureMap.put(key, texture);
        }

        Texture result = textureMap.get(key);
        assert result != null;
        return result;
    }

    /**
     * Access the GLFW window.
     *
     * @return the handle of the pre-existing window (not null)
     */
    static long glfwWindowHandle() {
        assert windowHandle != MemoryUtil.NULL;
        return windowHandle;
    }

    /**
     * Hide the specified geometries.
     *
     * @param geometries the geometries to de-visualize (not null, unaffected)
     */
    public static void hideAll(Collection<Geometry> geometries) {
        visibleGeometries.removeAll(geometries);
    }

    /**
     * Test whether the debugging aids are enabled.
     *
     * @return true if enabled, otherwise false
     */
    public static boolean isDebuggingEnabled() {
        return Internals.isDebuggingEnabled();
    }

    /**
     * Enumerate all the visible geometries.
     *
     * @return an unmodifiable collection of pre-existing objects
     */
    public static Collection<Geometry> listVisible() {
        return Collections.unmodifiableCollection(visibleGeometries);
    }

    /**
     * Make the specified Geometry visible.
     *
     * @param geometry the Geometry to visualize (not null, unaffected)
     */
    public static void makeVisible(Geometry geometry) {
        assert geometry.getMesh() != null;
        assert geometry.getProgram() != null;

        visibleGeometries.add(geometry);
    }

    /**
     * Alter the background color of the window.
     *
     * @param desiredColor the desired color (not null, unaffected,
     * default=black)
     */
    public static void setBackgroundColor(Vector4fc desiredColor) {
        Internals.setBackgroundColor(desiredColor);
    }

    /**
     * Alter the title of the window.
     *
     * @param text the desired text (in UTF-8 encoding)
     */
    public static void setWindowTitle(CharSequence text) {
        GLFW.glfwSetWindowTitle(windowHandle, text);
    }

    /**
     * Start the application (simplified interface).
     */
    public void start() {
        String appName = getClass().getSimpleName();
        start(appName, 0, 0, 0);
    }

    /**
     * Start the application.
     *
     * @param appName the name of the application (not null)
     * @param appMajor the major version number of the application
     * @param appMinor the minor version number of the application
     * @param appPatch the patch version number of the application
     */
    public void start(
            String appName, int appMajor, int appMinor, int appPatch) {
        // Generate the initial text for the window's title bar:
        String title;
        if (appMajor == 0 && appMinor == 0 && appPatch == 0) {
            title = appName;
        } else {
            title = String.format(
                    "%s v%d.%d.%d", appName, appMajor, appMinor, appPatch);
        }

        try {
            initializeGlfw(title);

            // Create and initialize the InputManager.
            inputManager = new InputManager(windowHandle);

            int appVersion = VK10.VK_MAKE_VERSION(appMajor, appMinor, appPatch);
            Internals.initializeVulkan(appName, appVersion, this);

            cameraInputProcessor = new CameraInputProcessor(windowHandle);
            inputManager.add(cameraInputProcessor);

            inputManager.add(new InputProcessor() {
                @Override
                public void onKeyboard(int keyId, boolean isPress) {
                    if (keyId == GLFW.GLFW_KEY_ESCAPE) { // stop the application
                        GLFW.glfwSetWindowShouldClose(windowHandle, true);
                        return;
                    }
                    super.onKeyboard(keyId, isPress);
                }
            });

            inputManager.add(new InputProcessor() {
                @Override
                public void onKeyboard(int keyId, boolean isPressed) {
                    if (keyId == GLFW.GLFW_KEY_C) {
                        if (isPressed) { // print camera state
                            Camera cam = getCamera();
                            System.out.println(cam);
                            System.out.flush();
                        }
                        return;
                    }
                    super.onKeyboard(keyId, isPressed);
                }
            });

            // Initialize the subclass.
            initialize();

            mainUpdateLoop();

            // Clean up the subclass.
            cleanUp();

        } catch (Exception exception) {
            System.err.print("Caught ");
            exception.printStackTrace();
            System.err.flush();

        } finally {
            // Clean up this class.
            cleanUpBase();
        }
    }
    // *************************************************************************
    // new protected methods

    /**
     * Callback invoked after the main update loop terminates.
     */
    protected abstract void cleanUp();

    /**
     * Callback invoked before the main update loop begins.
     */
    abstract protected void initialize();

    /**
     * Callback invoked during each iteration of the main update loop. Meant to
     * be overridden.
     */
    protected void render() {
        // do nothing
    }
    // *************************************************************************
    // private methods

    /**
     * Cleanly terminate the application after the window closes for any reason.
     */
    void cleanUpBase() {
        if (inputManager != null) {
            inputManager = inputManager.destroy();
        }
        Internals.cleanUpVulkan();
        cleanUpGlfw();
    }

    /**
     * Destroy the window and terminate GLFW.
     */
    private void cleanUpGlfw() {
        if (windowHandle != MemoryUtil.NULL) {
            GLFWFramebufferSizeCallback resizeCallback
                    = GLFW.glfwSetFramebufferSizeCallback(windowHandle, null);
            if (resizeCallback != null) {
                resizeCallback.free();
            }

            GLFW.glfwDestroyWindow(windowHandle);
            windowHandle = MemoryUtil.NULL;
        }

        // Cancel the error callback:
        GLFWErrorCallback errorCallback = GLFW.glfwSetErrorCallback(null);
        if (errorCallback != null) {
            errorCallback.free();
        }

        GLFW.glfwTerminate();
    }

    /**
     * Callback to trigger a resize of the frame buffers.
     *
     * @param window the affected window
     * @param width the new framebuffer width
     * @param height the new framebuffer height
     */
    private static void frameBufferResizeCallback(
            long window, int width, int height) {
        assert window == windowHandle :
                "window=" + window + " handle=" + windowHandle;
        /*
         * For drivers/platforms that don't return VK_ERROR_OUT_OF_DATE_KHR
         * or VK_SUBOPTIMAL_KHR after a window resize,
         * we use this fallback mechanism.
         */
        Internals.setNeedsResize();
    }

    /**
     * Initialize GLFW and create a window for the application.
     *
     * @param initialTitle the initial text for the window's title bar (not
     * null)
     */
    private static void initializeGlfw(String initialTitle) {
        if (Internals.isDebuggingEnabled()) {
            Configuration.DEBUG.set(true);
            Configuration.DEBUG_FUNCTIONS.set(true);
            Configuration.DEBUG_LOADER.set(true);
            Configuration.DEBUG_MEMORY_ALLOCATOR.set(true);
            Configuration.DEBUG_MEMORY_ALLOCATOR_INTERNAL.set(true);
            //Configuration.DEBUG_MEMORY_ALLOCATOR_FAST.set(true);
            Configuration.DEBUG_STACK.set(true);
        }

        // Report GLFW errors to System.err:
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        /*
         * GLFW was designed for use with OpenGL, so we explicitly tell it
         * not to create an OpenGL context.
         */
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        // Create the window:
        int width = Internals.framebufferWidth();
        int height = Internals.framebufferHeight();
        windowHandle = GLFW.glfwCreateWindow(
                width, height, initialTitle, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            throw new RuntimeException("Failed to create a GLFW window");
        }

        // Request callback when the frame buffer is resized:
        GLFW.glfwSetFramebufferSizeCallback(
                windowHandle, BaseApplication::frameBufferResizeCallback);
    }

    /**
     * The application's main update loop.
     */
    private void mainUpdateLoop() {
        while (!GLFW.glfwWindowShouldClose(windowHandle)) {
            updateBase();
        }
    }

    /**
     * The body of the main update loop.
     */
    private void updateBase() {
        render();
        GLFW.glfwPollEvents();
        cameraInputProcessor.update();
        Internals.renderNextFrame();
    }
}
