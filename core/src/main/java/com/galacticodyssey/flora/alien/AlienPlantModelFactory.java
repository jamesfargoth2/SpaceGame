package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

/** Uploads {@link AlienPlantMeshData} (stride 11) into a libGDX {@link Model}. Requires GL. */
public final class AlienPlantModelFactory {
    private static final VertexAttributes ATTRS = new VertexAttributes(
        new VertexAttribute(Usage.Position, 3, "a_position"),
        new VertexAttribute(Usage.Normal, 3, "a_normal"),
        new VertexAttribute(Usage.ColorUnpacked, 4, "a_color"),
        new VertexAttribute(Usage.Generic, 1, "a_emissive"));

    private AlienPlantModelFactory() {}

    public static Model toModel(AlienPlantMeshData data) {
        Mesh mesh = new Mesh(true, data.vertices.length / AlienPlantMeshBuilder.STRIDE, data.indices.length, ATTRS);
        mesh.setVertices(data.vertices);
        mesh.setIndices(data.indices);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("alien", mesh, GL20.GL_TRIANGLES, new Material());
        return mb.end();
    }
}
