package com.galacticodyssey.flora.grass;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;

/** Builds the shared base tuft mesh (a few crossed blades, unit height) with instanced
 *  attributes enabled. One mesh, reused for every instance. */
public final class GrassBladeMesh {
    private GrassBladeMesh() {}

    /** Creates a tuft of {@code blades} crossed quads. Base at y=0, tip at y=1, width ~0.1.
     *  Vertex layout: position(3) + normal(3). Instanced attrs: i_offset(3), i_params(4), i_color(3). */
    public static Mesh create(int blades, int maxInstances) {
        int vertsPerBlade = 4;       // quad
        int idxPerBlade = 6;
        float[] verts = new float[blades * vertsPerBlade * 6];
        short[] idx = new short[blades * idxPerBlade];
        float halfW = 0.05f;

        int vo = 0, io = 0;
        for (int b = 0; b < blades; b++) {
            double ang = Math.PI * b / blades; // spread blades around Y
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            // quad corners: bottom-left, bottom-right, top-right, top-left (top narrows slightly)
            float[][] corners = {
                { -halfW * c, 0f, -halfW * s }, {  halfW * c, 0f,  halfW * s },
                {  halfW * c * 0.4f, 1f,  halfW * s * 0.4f }, { -halfW * c * 0.4f, 1f, -halfW * s * 0.4f }
            };
            // face normal ~ perpendicular to the blade plane (in XZ), pointing up-ish
            float nx = -s, nz = c;
            int base = vo / 6;
            for (float[] p : corners) {
                verts[vo++] = p[0]; verts[vo++] = p[1]; verts[vo++] = p[2];
                verts[vo++] = nx;   verts[vo++] = 0.4f; verts[vo++] = nz;
            }
            idx[io++] = (short) base;       idx[io++] = (short) (base + 1); idx[io++] = (short) (base + 2);
            idx[io++] = (short) base;       idx[io++] = (short) (base + 2); idx[io++] = (short) (base + 3);
        }

        Mesh mesh = new Mesh(true, verts.length / 6, idx.length,
            new VertexAttribute(Usage.Position, 3, "a_position"),
            new VertexAttribute(Usage.Normal, 3, "a_normal"));
        mesh.setVertices(verts);
        mesh.setIndices(idx);

        mesh.enableInstancedRendering(false, maxInstances,
            new VertexAttribute(Usage.Generic, 3, "i_offset", 0),
            new VertexAttribute(Usage.Generic, 4, "i_params", 1),
            new VertexAttribute(Usage.Generic, 3, "i_color", 2));
        return mesh;
    }
}
