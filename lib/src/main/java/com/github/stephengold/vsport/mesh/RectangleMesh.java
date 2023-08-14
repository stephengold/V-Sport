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
package com.github.stephengold.vsport.mesh;

import com.github.stephengold.vsport.Mesh;
import com.github.stephengold.vsport.Topology;

/**
 * An indexed TriangleList mesh that renders an axis-aligned rectangle in the
 * X-Y plane.
 * <p>
 * In mesh space, the rectangle extends from (x0,y0,0) uv=(0,0) to (x2,y2,0)
 * uv=(1,1).
 *
 * @author Stephen Gold sgold@sonic.net
 */
public class RectangleMesh extends Mesh {
    // *************************************************************************
    // constructors

    /**
     * Instantiate an axis-aligned unit square with its first vertex at the mesh
     * origin.
     */
    public RectangleMesh() {
        this(0f, 1f, 0f, 1f, 1f);
    }

    /**
     * Instantiate an axis-aligned rectangle.
     *
     * @param x0 the X coordinate of the first vertex (in mesh coordinates)
     * @param x2 the X coordinate of the 3rd vertex (in mesh coordinates)
     * @param y0 the Y coordinate of the first vertex (in mesh coordinates)
     * @param y2 the Y coordinate of the 3rd vertex (in mesh coordinates)
     * @param normalZ the Z component of the normal vector (in mesh coordinates,
     * must be +1 or -1)
     */
    public RectangleMesh(
            float x0, float x2, float y0, float y2, float normalZ) {
        super(Topology.TriangleList, 4);
        // TODO use TriangleFan

        super.setPositions(
                x0, y0, 0f,
                x2, y0, 0f,
                x2, y2, 0f,
                x0, y2, 0f
        );
        super.setNormals(
                0f, 0f, normalZ,
                0f, 0f, normalZ,
                0f, 0f, normalZ,
                0f, 0f, normalZ
        );
        super.setUvs(
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f
        );
        /*
         * The vertex order for right-handed winding depends on the vertex
         * coordinates and also on the direction of the normal vector.
         */
        if ((x2 - x0) * (y2 - y0) * normalZ < 0f) {
            super.setIndices(0, 2, 1, 0, 3, 2);
        } else {
            super.setIndices(0, 1, 2, 0, 2, 3);
        }
    }
}
