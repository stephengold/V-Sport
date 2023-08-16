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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import jme3utilities.MyString;
import org.lwjgl.system.MemoryStack;

/**
 * Used to load and cache textures. Note: immutable.
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class TextureKey {
    // *************************************************************************
    // fields

    /**
     * option for flipping axes (not null)
     */
    final private FlipAxes flipAxes;
    /**
     * default setting for the {@code flipAxes} parameter (not null)
     */
    private static FlipAxes flipAxesDefault = FlipAxes.noFlip;
    /**
     * true to generate MIP maps, false to skip generating them
     */
    final private boolean mipmaps;
    /**
     * default setting for mipmaps
     */
    private static boolean mipmapsDefault = true;
    /**
     * URI to load/generate image data
     */
    final private URI uri;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a key with the specified URI.
     *
     * @param uriString unparsed URI to load/generate image data (not null, not
     * empty)
     */
    public TextureKey(String uriString) {
        this(uriString, mipmapsDefault, flipAxesDefault);
    }

    /**
     * Instantiate a custom key.
     *
     * @param uriString unparsed URI to load/generate image data (not null, not
     * empty)
     * @param mipmaps true to generate MIP maps, false to skip
     * @param flipAxes option for flipping axes (not null)
     */
    public TextureKey(String uriString, boolean mipmaps, FlipAxes flipAxes) {
        // It's better to report URI errors now than during load()!
        validateUriString(uriString);

        try {
            this.uri = new URI(uriString);
        } catch (URISyntaxException exception) {
            throw new RuntimeException(uriString); // shouldn't occur
        }

        this.mipmaps = mipmaps;
        this.flipAxes = flipAxes;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Load/generate the Texture for this key.
     *
     * @return a new instance
     */
    Texture load() {
        Texture result;

        String scheme = uri.getScheme();
        if (scheme.equals("procedural")) {
            String path = uri.getPath();
            String query = uri.getQuery();
            result = synthesizeTexture(path, query);

        } else {
            InputStream stream;
            if (scheme.equals("classpath")) {
                String path = uri.getPath();
                stream = Utils.class.getResourceAsStream(path);

            } else { // The URI must also be a URL.
                URL url;
                try {
                    url = uri.toURL();
                } catch (MalformedURLException exception) {
                    throw new RuntimeException(exception);
                }
                try {
                    stream = url.openStream();
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            }

            result = Texture.newInstance(stream, uri, mipmaps, flipAxes);
        }

        return result;
    }

    /**
     * Test whether MIP maps should be generated during load().
     *
     * @return true if they should be generated, otherwise false
     */
    public boolean mipmaps() {
        return mipmaps;
    }

    /**
     * Alter the default {@code flipAxes} setting for new texture keys.
     *
     * @param flipAxes the setting to become the default (default=noFlip)
     */
    public static void setDefaultFlipAxes(FlipAxes flipAxes) {
        flipAxesDefault = flipAxes;
    }

    /**
     * Alter the default MIP-maps setting for new texture keys.
     *
     * @param enable the setting to become the default (default=true)
     */
    public static void setDefaultMipmaps(boolean enable) {
        mipmapsDefault = enable;
    }
    // *************************************************************************
    // Object methods

    /**
     * Test for equivalence with another Object.
     *
     * @param otherObject the object to compare to (may be null, unaffected)
     * @return true if the objects are equivalent, otherwise false
     */
    @Override
    public boolean equals(Object otherObject) {
        boolean result;
        if (otherObject == this) {
            result = true;

        } else if (otherObject != null
                && otherObject.getClass() == getClass()) {
            TextureKey otherKey = (TextureKey) otherObject;
            result = uri.equals(otherKey.uri)
                    && mipmaps == otherKey.mipmaps
                    && flipAxes == otherKey.flipAxes;

        } else {
            result = false;
        }

        return result;
    }

    /**
     * Generate the hash code for this key.
     *
     * @return a 32-bit value for use in hashing
     */
    @Override
    public int hashCode() {
        int hash = uri.hashCode();
        hash = 707 * hash + (mipmaps ? 1 : 0);
        hash = 707 * hash + flipAxes.hashCode();

        return hash;
    }

    /**
     * Represent this key as a String.
     *
     * @return a descriptive string of text (not null, not empty)
     */
    @Override
    public String toString() {
        String mm = mipmaps ? "+" : "-";
        String result = String.format(
                "TextureKey(%s%n %s %smipmaps)", uri, flipAxes, mm);

        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Generate a square texture for a 2-by-2 checkerboard pattern.
     *
     * @param argMap to map argument names to values (not null, unaffected)
     * @return a new instance
     */
    private Texture synthesizeCheckerboard(Map<String, String> argMap) {
        String sizeDecimal = argMap.get("size");
        if (sizeDecimal == null) {
            sizeDecimal = "64";
        }
        int size = Integer.parseInt(sizeDecimal);
        if (size < 1) {
            throw new IllegalArgumentException("size = " + size);
        }

        String c0Arg = argMap.get("color0");
        if (c0Arg == null) {
            c0Arg = "000000ff"; // black
        }

        String c1Arg = argMap.get("color1");
        if (c1Arg == null) {
            c1Arg = "ffffffff"; // white
        }

        int halfSize = size / 2;
        int bytesPerTexel = 4;
        int numBytes = size * size * bytesPerTexel;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer color0 = Utils.hexToBytes(c0Arg, stack);
            ByteBuffer color1 = Utils.hexToBytes(c1Arg, stack);

            Texture result = new Texture(numBytes, size, size, mipmaps) {
                @Override
                protected void fill(ByteBuffer pixels) {
                    for (int y = 0; y < size; ++y) {
                        int ySide = y / halfSize;
                        for (int x = 0; x < size; ++x) {
                            int xSide = x / halfSize;
                            int colorIndex = (xSide + ySide) % 2;
                            ByteBuffer color
                                    = (colorIndex == 0) ? color0 : color1;
                            color.rewind();
                            pixels.put(color);
                        }
                    }
                }
            };

            return result;
        }

    }

    /**
     * Synthesize a texture using parameters encoded in a path string and a
     * query string.
     *
     * @param path the path string to parse (not null)
     * @param query the query string to parse (not null)
     * @return a new texture (not null)
     */
    private Texture synthesizeTexture(String path, String query) {
        Map<String, String> queryMap = new HashMap<>(16);
        if (query != null) {
            String[] assignments = query.split("&");
            for (String assignment : assignments) {
                String[] terms = assignment.split("=", 2);
                String name = terms[0];
                String value = terms[1];
                queryMap.put(name, value);
            }
        }

        Texture result;
        switch (path) {
            case "/checkerboard":
                result = synthesizeCheckerboard(queryMap);
                break;

            default:
                String qPath = MyString.quote(path);
                String qQuery = MyString.quote(query);
                throw new IllegalArgumentException(
                        "path=" + qPath + ", query=" + qQuery);
        }

        return result;
    }

    /**
     * Verify that the argument is a valid URI for streaming data.
     *
     * @param uriString the string to test (not null)
     */
    private static void validateUriString(String uriString) {
        URI uri;
        try {
            uri = new URI(uriString);
        } catch (URISyntaxException exception) {
            String message = System.lineSeparator()
                    + " uriString = " + MyString.quote(uriString);
            throw new IllegalArgumentException(message, exception);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            String message = "no scheme in " + MyString.quote(uriString);
            throw new IllegalArgumentException(message);

        } else if (scheme.equals("procedural")) {
            String path = uri.getPath();
            if (path == null) {
                String message = "no path in " + MyString.quote(uriString);
                throw new IllegalArgumentException(message);
            }

        } else if (scheme.equals("classpath")) {
            String path = uri.getPath();
            if (path == null) {
                String message = "no path in " + MyString.quote(uriString);
                throw new IllegalArgumentException(message);
            }

            InputStream stream = Utils.class.getResourceAsStream(path);
            if (stream == null) {
                String message = "resource not found:  " + MyString.quote(path);
                throw new IllegalArgumentException(message);
            }
            try {
                stream.close();
            } catch (IOException exception) {
                // do nothing
            }

        } else {
            URL url;
            try {
                url = uri.toURL();
            } catch (MalformedURLException exception) {
                String message = System.lineSeparator()
                        + " uriString = " + MyString.quote(uriString);
                throw new IllegalArgumentException(message, exception);
            }

            InputStream stream;
            try {
                stream = url.openStream();
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException exception) {
                    // do nothing
                }
            }
        }
    }
}
