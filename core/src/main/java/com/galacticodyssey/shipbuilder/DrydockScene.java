package com.galacticodyssey.shipbuilder;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.ship.HullGeometry;
import com.galacticodyssey.ship.ShipHullGenerator;

public class DrydockScene implements Disposable {
    private final Environment environment;
    private final ModelBatch modelBatch;
    private final ShipHullGenerator hullGenerator = new ShipHullGenerator();
    private Mesh hullMesh;
    private Model hullModel;
    private ModelInstance hullInstance;
    private boolean meshDirty = true;
    private long lastRegenTime;
    private static final long REGEN_THROTTLE_MS = 100;

    public DrydockScene() {
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.35f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.9f, -0.5f, -1f, -0.3f));
    }

    public void markMeshDirty() {
        meshDirty = true;
    }

    public void updateMesh(ShipDesign design) {
        if (!meshDirty) return;
        long now = System.currentTimeMillis();
        if (now - lastRegenTime < REGEN_THROTTLE_MS) return;
        lastRegenTime = now;

        if (hullModel != null) hullModel.dispose();
        if (hullMesh != null) hullMesh.dispose();

        HullGeometry hull = hullGenerator.generate(design.toBlueprint());
        hullMesh = new Mesh(true, hull.vertexCount(), hull.indices.length,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
            new VertexAttribute(VertexAttributes.Usage.ColorPacked, 4, "a_color"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_emissive"));
        hullMesh.setVertices(hull.vertices);
        hullMesh.setIndices(hull.indices);

        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        mb.part("hull", hullMesh, GL20.GL_TRIANGLES,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        hullModel = mb.end();
        hullInstance = new ModelInstance(hullModel);
        meshDirty = false;
    }

    public void render(Camera camera) {
        modelBatch.begin(camera);
        if (hullInstance != null) {
            modelBatch.render(hullInstance, environment);
        }
        modelBatch.end();
    }

    public HullGeometry getCurrentHullGeometry(ShipDesign design) {
        return hullGenerator.generate(design.toBlueprint());
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        if (hullModel != null) hullModel.dispose();
        if (hullMesh != null) hullMesh.dispose();
    }
}
