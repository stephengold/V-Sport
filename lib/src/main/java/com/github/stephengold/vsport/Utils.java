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

import com.jme3.util.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import javax.imageio.ImageIO;
import jme3utilities.MyString;
import jme3utilities.Validate;
import jme3utilities.math.MyMath;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

/**
 * Public utility methods in the V-Sport library.
 *
 * @author Stephen Gold sgold@sonic.net
 */
final public class Utils {
    // *************************************************************************
    // constructors

    /**
     * A private constructor to inhibit instantiation of this class.
     */
    private Utils() {
        // do nothing
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Calculate the offset of a UBO field, taking alignment into account.
     *
     * @param offset the offset of the byte following the previous field (&ge;0)
     * @param alignment the required alignment of the next field (in bytes,
     * &gt;0)
     * @return the offset of the first byte of the next field
     */
    static int align(int offset, int alignment) {
        Validate.nonNegative(offset, "offset");
        Validate.positive(alignment, "alignment");

        int excess = MyMath.modulo(offset, alignment);

        int result;
        if (excess == 0) {
            result = offset;
        } else {
            result = offset - excess + alignment;
        }

        assert result - offset < alignment;
        assert result % alignment == 0;
        return result;
    }

    /**
     * Append a UTF-8 string pointer to the specified PointerBuffer.
     *
     * @param bufferIn (not null, unaffected)
     * @param string the text string to append (not null)
     * @param stack for allocating temporary host buffers (not null)
     * @return a new, flipped, temporary buffer (not null)
     */
    static PointerBuffer appendStringPointer(
            PointerBuffer bufferIn, String string, MemoryStack stack) {
        int oldCapacity = bufferIn.capacity();
        int newCapacity = oldCapacity + 1;
        PointerBuffer result = stack.mallocPointer(newCapacity);
        for (int i = 0; i < oldCapacity; ++i) {
            long pointer = bufferIn.get(i);
            result.put(pointer);
        }

        ByteBuffer utf8Name = stack.UTF8(string);
        result.put(utf8Name);
        result.flip();

        return result;
    }

    /**
     * Throw a runtime exception if an operation returned a code other than
     * {@code VK_SUCCESS} or {S@code VK_SUBOPTIMAL}.
     *
     * @param description a textual description of the operation
     * @param returnCode a code returned by the GLFW API or the Vulkan API
     */
    static void checkForError(int returnCode, String description) {
        if (returnCode != VK10.VK_SUCCESS
                && returnCode != KHRSwapchain.VK_SUBOPTIMAL_KHR) {
            String message = String.format(
                    "Failed to %s, retCode=%d", description, returnCode);
            throw new RuntimeException(message);
        }
    }

    /**
     * Convert the specified VK_INDEX_TYPE_... code to text.
     *
     * @param code the code to decipher
     * @return a descriptive string of text (not null, not empty)
     */
    static String describeIndexType(int code) {
        switch (code) {
            case VK10.VK_INDEX_TYPE_UINT16:
                return "UINT16";
            case VK10.VK_INDEX_TYPE_UINT32:
                return "UINT32";

            default:
                return "unknown" + code;
        }
    }

    /**
     * Write the specified 3x3 matrix to the specified buffer in column-major
     * order, aligning each column to 16 bytes.
     *
     * @param matrix the matrix to write (not null, unaffected)
     * @param startOffset the buffer offset at which to start writing (in bytes)
     * @param target the buffer to write to (not null, limit/mark/position
     * unaffected)
     * @return the offset of the first unwritten byte after the write
     */
    static int getToBuffer(
            Matrix3f matrix, int startOffset, ByteBuffer target) {
        int offset = Utils.align(startOffset, 16);
        target.putFloat(offset, matrix.m00());
        target.putFloat(offset + 4, matrix.m01());
        target.putFloat(offset + 8, matrix.m02());

        target.putFloat(offset + 16, matrix.m10());
        target.putFloat(offset + 20, matrix.m11());
        target.putFloat(offset + 24, matrix.m12());

        target.putFloat(offset + 32, matrix.m20());
        target.putFloat(offset + 36, matrix.m21());
        target.putFloat(offset + 40, matrix.m22());

        int result = offset + 44;
        return result;
    }

    /**
     * Test whether the specified buffer format includes a stencil component.
     *
     * @param format the buffer format to test
     * @return true if it includes a stencil component, otherwise false
     */
    static boolean hasStencilComponent(int format) {
        switch (format) {
            case VK10.VK_FORMAT_D16_UNORM_S8_UINT:
            case VK10.VK_FORMAT_D24_UNORM_S8_UINT:
            case VK10.VK_FORMAT_D32_SFLOAT_S8_UINT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse a hexadecimal string to a ByteBuffer.
     *
     * @param hexString a hexadecimal string up to 8 characters long (not null,
     * not empty)
     * @param stack for allocating temporary host buffers (not null)
     * @return a new temporary buffer (with limit=capacity=4, most-significant
     * byte in position 0, least-significant byte in position 3)
     *
     * @throws NumberFormatException if {@code hexString} fails to parse
     */
    static ByteBuffer hexToBytes(String hexString, MemoryStack stack) {
        int iValue = Integer.parseUnsignedInt(hexString, 16);
        byte b0 = (byte) (iValue >> 24);
        byte b1 = (byte) (iValue >> 16);
        byte b2 = (byte) (iValue >> 8);
        byte b3 = (byte) iValue;

        ByteBuffer result = stack.bytes(b0, b1, b2, b3);
        result.limit(4);

        return result;
    }

    /**
     * Load raw bytes from the named classpath resource.
     *
     * @param resourceName the name of the resource (not null)
     * @return a new array
     */
    public static ByteBuffer loadResourceAsBytes(String resourceName) {
        // Read the resource to determine its size in bytes:
        InputStream inputStream = Utils.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }
        int totalBytes = 0;
        byte[] tmpArray = new byte[4096];
        try {
            while (true) {
                int numBytesRead = inputStream.read(tmpArray);
                if (numBytesRead < 0) {
                    break;
                }
                totalBytes += numBytesRead;
            }
            inputStream.close();

        } catch (IOException exception) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("failed to read resource " + q);
        }
        ByteBuffer result = BufferUtils.createByteBuffer(totalBytes);

        // Read the resource again to fill the buffer with data:
        inputStream = Utils.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }
        try {
            while (true) {
                int numBytesRead = inputStream.read(tmpArray);
                if (numBytesRead == tmpArray.length) {
                    result.put(tmpArray);
                } else {
                    assert numBytesRead >= 0 : numBytesRead;
                    for (int i = 0; i < numBytesRead; ++i) {
                        byte b = tmpArray[i];
                        result.put(b);
                    }
                    break;
                }
            }
            inputStream.close();

        } catch (IOException exception) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("failed to read resource " + q);
        }

        result.flip();
        return result;
    }

    /**
     * Load an AWT BufferedImage from the named classpath resource.
     *
     * @param resourceName the name of the resource (not null)
     * @return a new instance
     */
    public static BufferedImage loadResourceAsImage(String resourceName) {
        InputStream inputStream = Utils.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }

        ImageIO.setUseCache(false);

        try {
            BufferedImage result = ImageIO.read(inputStream);
            return result;

        } catch (IOException exception) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("unable to read " + q);
        }
    }

    /**
     * Load UTF-8 text from the named resource.
     *
     * @param resourceName the name of the classpath resource to load (not null)
     * @return the text (possibly multiple lines)
     */
    public static String loadResourceAsString(String resourceName) {
        InputStream inputStream = Utils.class.getResourceAsStream(resourceName);
        if (inputStream == null) {
            String q = MyString.quote(resourceName);
            throw new RuntimeException("resource not found:  " + q);
        }

        Scanner scanner
                = new Scanner(inputStream, StandardCharsets.UTF_8.name());
        String result = scanner.useDelimiter("\\A").next();

        return result;
    }

    /**
     * Calculate the floor of the base-2 logarithm of the input value.
     *
     * @param iValue the input value (&gt;0)
     * @return the largest integer N &le 30 for which {@code (1 << N) <= iValue}
     * (&ge;0, &le;30)
     */
    static int log2(int iValue) {
        Validate.positive(iValue, "input value");
        int result = 31 - Integer.numberOfLeadingZeros(iValue);
        return result;
    }

    /**
     * Find the maximum of some int values.
     *
     * @param iValues the input values
     * @return the most positive value
     * @see Collections#max()
     * @see java.lang.Math#max(int, int)
     */
    public static int maxInt(int... iValues) {
        int result = Integer.MIN_VALUE;
        for (int iValue : iValues) {
            if (iValue > result) {
                result = iValue;
            }
        }

        return result;
    }

    /**
     * Copy the specified JOML vector to a JME vector.
     *
     * @param vector3f the JOML vector to copy (not null, unaffected)
     * @return a new JME vector (not null)
     */
    public static com.jme3.math.Vector3f toJmeVector(Vector3fc vector3f) {
        com.jme3.math.Vector3f result = new com.jme3.math.Vector3f(
                vector3f.x(), vector3f.y(), vector3f.z());
        return result;
    }

    /**
     * Copy the specified JME vector to a JOML vector.
     *
     * @param vector3f the JME vector to copy (not null, unaffected)
     * @return a new JOML vector (not null)
     */
    public static Vector3f toJomlVector(com.jme3.math.Vector3f vector3f) {
        return new Vector3f(vector3f.x, vector3f.y, vector3f.z);
    }

    /**
     * Convert an sRGB color string to a color in the linear colorspace.
     *
     * @param hexString the input color (hexadecimal string with red channel in
     * the most-significant byte, alpha channel in the least significant byte)
     * @return a new vector (red channel in the X component, alpha channel in
     * the W component)
     *
     * @throws NumberFormatException if {@code hexString} fails to parse
     */
    public static Vector4f toLinearColor(String hexString) {
        int srgbColor = Integer.parseUnsignedInt(hexString, 16);

        double red = ((srgbColor >> 24) & 0xFF) / 255.0;
        double green = ((srgbColor >> 16) & 0xFF) / 255.0;
        double blue = ((srgbColor >> 8) & 0xFF) / 255.0;

        // linearize the color channels
        float r = (float) Math.pow(red, 2.2);
        float g = (float) Math.pow(green, 2.2);
        float b = (float) Math.pow(blue, 2.2);

        float a = (srgbColor & 0xFF) / 255f;

        return new Vector4f(r, g, b, a);
    }

    /**
     * Convert the specified vector from Cartesian coordinates to spherical
     * coordinates (r, theta, phi) per ISO 80000.
     * <p>
     * In particular:
     * <ul>
     * <li>{@code r} is a distance measured from the origin. It ranges from 0 to
     * infinity and is stored in the first (X) vector component.
     *
     * <li>{@code theta} is the polar angle, measured (in radians) from the +Z
     * axis. It ranges from 0 to PI and is stored in the 2nd (Y) vector
     * component.
     *
     * <li>{@code phi} is the azimuthal angle, measured (in radians) from the +X
     * axis to the projection of the vector onto the X-Y plane. It ranges from
     * -PI to PI and is stored in the 3rd (Z) vector component.
     * </ul>
     *
     * @param vec the vector to convert (not null, modified)
     */
    public static void toSpherical(com.jme3.math.Vector3f vec) {
        double xx = vec.x;
        double yy = vec.y;
        double zz = vec.z;
        double sumOfSquares = xx * xx + yy * yy;
        double rxy = Math.sqrt(sumOfSquares);
        double theta = Math.atan2(yy, xx);
        sumOfSquares += zz * zz;
        double phi = Math.atan2(rxy, zz);
        double rr = Math.sqrt(sumOfSquares);

        vec.x = (float) rr;    // distance from origin
        vec.y = (float) theta; // polar angle
        vec.z = (float) phi;   // azimuthal angle
    }
}
