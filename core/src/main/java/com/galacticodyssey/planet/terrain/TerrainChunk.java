package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;

public final class TerrainChunk implements Disposable {
    public static final int MAX_DEPTH = 8;
    public static final float SPLIT_THRESHOLD = 1.5f;
    public static final float MERGE_THRESHOLD = 0.75f;

    public final CubeFace face;
    public final int depth;
    public final float u0, v0, u1, v1;
    /** Planet-space centre of this chunk in kilometres. */
    public final PlanetCoordsKM centerPlanetKm;
    /** Visual arc span in kilometres. */
    public final double arcLengthKm;
    /** Placement of this chunk's local-metre mesh in the current floating-origin frame. */
    public LocalCoordsM placementLocal = new LocalCoordsM(0, 0, 0);
    public TerrainChunk[] children;
    public Mesh mesh;
    public boolean meshReady;
    public btRigidBody collisionBody;
    public btTriangleMesh triangleMesh;
    public btBvhTriangleMeshShape collisionShape;

    public TerrainChunk(CubeFace face, int depth, float u0, float v0, float u1, float v1, double radiusKm) {
        this.face = face;
        this.depth = depth;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        com.badlogic.gdx.math.Vector3 d = CubeSphere.toSphere(face, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f);
        this.centerPlanetKm = new PlanetCoordsKM(d.x * radiusKm, d.y * radiusKm, d.z * radiusKm);
        this.arcLengthKm = radiusKm * (u1 - u0) * 1.57;
        this.meshReady = false;
    }

    public boolean hasChildren() {
        return children != null;
    }

    public boolean shouldSplit(PlanetCoordsKM cameraKm) {
        double dist = centerPlanetKm.dst(cameraKm);
        double screenSize = arcLengthKm / Math.max(dist, 1e-6);
        return screenSize > SPLIT_THRESHOLD && depth < MAX_DEPTH;
    }

    public boolean shouldMerge(PlanetCoordsKM cameraKm) {
        double dist = centerPlanetKm.dst(cameraKm);
        double screenSize = arcLengthKm / Math.max(dist, 1e-6);
        return screenSize < MERGE_THRESHOLD;
    }

    @Override
    public void dispose() {
        disposeCollision(null);
        if (mesh != null) { mesh.dispose(); mesh = null; }
        if (children != null) {
            for (TerrainChunk child : children) child.dispose();
            children = null;
        }
    }

    public void disposeCollision(btDiscreteDynamicsWorld dynamicsWorld) {
        if (collisionBody != null) {
            if (dynamicsWorld != null) {
                dynamicsWorld.removeRigidBody(collisionBody);
            }
            collisionBody.dispose();
            collisionBody = null;
        }
        if (collisionShape != null) { collisionShape.dispose(); collisionShape = null; }
        if (triangleMesh != null) { triangleMesh.dispose(); triangleMesh = null; }
    }
}
