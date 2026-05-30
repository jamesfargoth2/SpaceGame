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
    public static final int MAX_DEPTH = 14;
    public static final float SPLIT_THRESHOLD = 1.5f;
    public static final float MERGE_THRESHOLD = 0.75f;

    public final CubeFace face;
    public final int depth;
    public final float u0, v0, u1, v1;
    /** Sphere radius in kilometres (used to project chunk corners for the nearest-point LOD metric). */
    private final double radiusKm;
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

    /** Lifecycle of this chunk's geometry. */
    public enum State { EMPTY, PENDING, GENERATING, READY }
    public State state = State.EMPTY;
    /** True when this chunk should carry a Bullet collision body (set by the collision gate). */
    public boolean wantsCollision;

    /** Bumped whenever this chunk's geometry is invalidated (split/merge), so async results
     *  generated against an older token can be detected and discarded. */
    private int generationToken;
    public int generationToken() { return generationToken; }
    public void invalidate() { generationToken++; state = State.EMPTY; }

    public TerrainChunk(CubeFace face, int depth, float u0, float v0, float u1, float v1, double radiusKm) {
        this.face = face;
        this.depth = depth;
        this.u0 = u0;
        this.v0 = v0;
        this.u1 = u1;
        this.v1 = v1;
        this.radiusKm = radiusKm;
        com.badlogic.gdx.math.Vector3 d = CubeSphere.toSphere(face, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f);
        this.centerPlanetKm = new PlanetCoordsKM(d.x * radiusKm, d.y * radiusKm, d.z * radiusKm);
        this.arcLengthKm = radiusKm * (u1 - u0) * 1.57;
        this.meshReady = false;
    }

    public boolean hasChildren() {
        return children != null;
    }

    /**
     * Distance in km from the camera to the NEAREST point of this chunk, approximated as the
     * minimum over the chunk's four projected corners plus its centre. On a cube-sphere the
     * centroid of a face-quadrant projects ~45° toward the equator, so a centroid-only metric
     * leaves the under-camera cell far away and the quadtree stalls. The nearest-corner metric
     * shrinks toward 0 as a cell tightens under the camera, so it keeps subdividing to MAX_DEPTH.
     */
    private double nearestDistKm(PlanetCoordsKM cameraKm) {
        double best = centerPlanetKm.dst(cameraKm);
        float[][] uv = {{u0, v0}, {u1, v0}, {u0, v1}, {u1, v1}};
        for (float[] c : uv) {
            com.badlogic.gdx.math.Vector3 d = CubeSphere.toSphere(face, c[0], c[1]);
            double dx = d.x * radiusKm - cameraKm.x();
            double dy = d.y * radiusKm - cameraKm.y();
            double dz = d.z * radiusKm - cameraKm.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < best) best = dist;
        }
        return best;
    }

    public boolean shouldSplit(PlanetCoordsKM cameraKm) {
        double dist = nearestDistKm(cameraKm);
        double screenSize = arcLengthKm / Math.max(dist, 1e-6);
        return screenSize > SPLIT_THRESHOLD && depth < MAX_DEPTH;
    }

    public boolean shouldMerge(PlanetCoordsKM cameraKm) {
        double dist = nearestDistKm(cameraKm);
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
