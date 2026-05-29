package com.galacticodyssey.flora.gen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/** Uploads {@link FloraMeshData} into a libGDX {@link Model}. Requires a GL context. */
public final class FloraModelFactory {
    private static final VertexAttributes ATTRS = new VertexAttributes(
        new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"));

    private FloraModelFactory() {}

    /** Builds a Model with a trunk part (+ foliage part when present). Caller owns disposal. */
    public static Model toModel(FloraMeshData data, Color trunkColor, Color foliageColor) {
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("trunk", makeMesh(data.trunkVertices, data.trunkIndices),
            GL20.GL_TRIANGLES, new Material(ColorAttribute.createDiffuse(trunkColor)));
        if (data.hasFoliage()) {
            mb.part("foliage", makeMesh(data.foliageVertices, data.foliageIndices),
                GL20.GL_TRIANGLES, new Material(ColorAttribute.createDiffuse(foliageColor)));
        }
        return mb.end();
    }

    private static Mesh makeMesh(float[] vertices, short[] indices) {
        Mesh mesh = new Mesh(true, vertices.length / 6, indices.length, ATTRS);
        mesh.setVertices(vertices);
        mesh.setIndices(indices);
        return mesh;
    }
}
