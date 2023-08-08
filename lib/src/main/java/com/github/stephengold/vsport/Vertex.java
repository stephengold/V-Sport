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

import java.nio.ByteBuffer;
import jme3utilities.Validate;
import org.joml.Vector2fc;
import org.joml.Vector3fc;

/**
 * The attributes of a single vertex in a mesh.
 *
 * @author Stephen Gold sgold@sonic.net
 *
 * Derived from the Vertex class in Cristian Herrera's Vulkan-Tutorial-Java
 * project.
 */
public class Vertex {
    // *************************************************************************
    // fields

    /**
     * texture coordinates (2 floats) or null if not present
     */
    final private Vector2fc texCoords;
    /**
     * vertex colors (3 floats) or null if not present
     */
    final private Vector3fc color;
    /**
     * vertex normal in mesh coordinates (3 floats) or null if not present
     */
    final private Vector3fc normal;
    /**
     * vertex position in mesh coordinates (3 floats)
     */
    final private Vector3fc position;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a mesh vertex from attribute values.
     *
     * @param position the desired location of the vertex (in model coordinates,
     * not null, alias created)
     * @param color the desired color of the vertex (alias created if not null))
     * @param normal the desired normal direction at the vertex (alias created
     * if not null))
     * @param texCoords the desired texture coordinates of the vertex (alias
     * created if not null)
     */
    Vertex(Vector3fc position, Vector3fc color, Vector3fc normal,
            Vector2fc texCoords) {
        Validate.nonNull(position, "position");

        this.position = position;
        this.color = color;
        this.normal = normal;
        this.texCoords = texCoords;
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Text whether the color attribute is present.
     *
     * @return true if present, otherwise false
     */
    public boolean hasColor() {
        if (color == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Text whether the normal attribute is present.
     *
     * @return true if present, otherwise false
     */
    public boolean hasNormal() {
        if (normal == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Test whether texture coordinates are present.
     *
     * @return true if present, otherwise false
     */
    public boolean hasTexCoords() {
        if (texCoords == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Write the vertex color data to the specified ByteBuffer, starting at the
     * current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeColorTo(ByteBuffer target) {
        target.putFloat(color.x());
        target.putFloat(color.y());
        target.putFloat(color.z());
    }

    /**
     * Write the vertex normal data to the specified ByteBuffer, starting at the
     * current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeNormalTo(ByteBuffer target) {
        target.putFloat(normal.x());
        target.putFloat(normal.y());
        target.putFloat(normal.z());
    }

    /**
     * Write the vertex position data to the specified ByteBuffer, starting at
     * the current buffer position) and advance the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writePositionTo(ByteBuffer target) {
        target.putFloat(position.x());
        target.putFloat(position.y());
        target.putFloat(position.z());
    }

    /**
     * Write the texture coordinate data to the specified ByteBuffer, starting
     * at the current buffer position) and advances the buffer position.
     *
     * @param target the buffer to write to (not null, modified)
     */
    void writeTexCoordsTo(ByteBuffer target) {
        target.putFloat(texCoords.x());
        target.putFloat(texCoords.y());
    }
    // *************************************************************************
    // Object methods

    /**
     * Test for exact equivalence with another Object.
     *
     * @param otherObject the object to compare (may be null, unaffected)
     * @return true if {@code this} and {@code otherObject} have identical
     * values, otherwise false
     */
    @Override
    public boolean equals(Object otherObject) {
        boolean result;
        if (otherObject == this) {
            result = true;

        } else if (otherObject != null
                && otherObject.getClass() == getClass()) {
            Vertex otherVertex = (Vertex) otherObject;
            result = otherVertex.position.equals(position);
            if (result && color != otherVertex.color) {
                result = (color != null && color.equals(otherVertex.color));
            }
            if (result && normal != otherVertex.normal) {
                result = (normal != null && color.equals(otherVertex.normal));
            }
            if (result && texCoords != otherVertex.texCoords) {
                result = (texCoords != null
                        && texCoords.equals(otherVertex.texCoords));
            }

        } else {
            result = false;
        }

        return result;
    }

    /**
     * Generate the hash code for this Vertex.
     *
     * @return a 32-bit value for use in hashing
     */
    @Override
    public int hashCode() {
        int result = 707;
        result = 29 * result + position.hashCode();
        if (color != null) {
            result = 29 * result + color.hashCode();
        }
        if (normal != null) {
            result = 31 * result + normal.hashCode();
        }
        if (texCoords != null) {
            result = 37 * result + texCoords.hashCode();
        }

        return result;
    }
    // *************************************************************************
    // Object methods

    /**
     * Represent the vertex as a text string.
     *
     * @return a descriptive string of text (not null)
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(80);

        result.append("xyz=(");
        result.append(position.x());
        result.append(", ");
        result.append(position.y());
        result.append(", ");
        result.append(position.z());
        result.append(")");

        if (color != null) {
            result.append(" color=(");
            result.append(color.x());
            result.append(", ");

            result.append(color.y());
            result.append(", ");

            result.append(color.z());
            result.append(")");
        }

        if (normal != null) {
            result.append(" normal=(");
            result.append(normal.x());
            result.append(", ");

            result.append(normal.y());
            result.append(", ");

            result.append(normal.z());
            result.append(")");
        }

        if (texCoords != null) {
            result.append(" uv=(");
            result.append(texCoords.x());
            result.append(", ");

            result.append(texCoords.y());
            result.append(")");
        }

        return result.toString();
    }
}
