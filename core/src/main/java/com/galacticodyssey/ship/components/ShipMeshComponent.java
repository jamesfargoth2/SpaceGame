package com.galacticodyssey.ship.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.utils.Disposable;

public class ShipMeshComponent implements Component, Disposable {
    public Mesh hullMesh;
    public int vertexStride;

    @Override
    public void dispose() {
        if (hullMesh != null) { hullMesh.dispose(); hullMesh = null; }
    }
}
