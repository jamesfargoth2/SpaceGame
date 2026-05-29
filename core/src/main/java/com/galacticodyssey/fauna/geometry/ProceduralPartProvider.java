package com.galacticodyssey.fauna.geometry;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;

/** Uploads {@link ProceduralMeshData} to a libGDX Model. */
public final class ProceduralPartProvider implements PartGeometryProvider {
    private final ProceduralPartMesher mesher = new ProceduralPartMesher();
    private final ModelBuilder modelBuilder = new ModelBuilder();

    @Override public boolean supports(PartGeometrySpec spec) {
        return spec.kind == PartGeometrySpec.Kind.PROCEDURAL;
    }

    @Override public Model buildPartModel(PartGeometrySpec spec) {
        ProceduralMeshData data = mesher.build(spec);
        modelBuilder.begin();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.GRAY));
        MeshPartBuilder mpb = modelBuilder.part("part", GL20.GL_TRIANGLES,
            Usage.Position | Usage.Normal, mat);
        float[] p = data.positions;
        for (int i = 0; i < data.indices.length; i += 3) {
            int a = data.indices[i] & 0xFFFF, b = data.indices[i + 1] & 0xFFFF, c = data.indices[i + 2] & 0xFFFF;
            mpb.triangle(vtemp(p, a), vtemp(p, b), vtemp(p, c));
        }
        return modelBuilder.end();
    }

    private static com.badlogic.gdx.math.Vector3 vtemp(float[] p, int idx) {
        return new com.badlogic.gdx.math.Vector3(p[idx * 3], p[idx * 3 + 1], p[idx * 3 + 2]);
    }

    @Override public void dispose() { /* ModelBuilder holds no GL state; built Models owned by caller */ }
}
