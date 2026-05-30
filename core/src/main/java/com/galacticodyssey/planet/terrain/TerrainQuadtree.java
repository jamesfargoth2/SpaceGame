package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBvhTriangleMeshShape;
import com.badlogic.gdx.physics.bullet.collision.btTriangleMesh;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.core.coords.CoordConvert;
import com.galacticodyssey.core.coords.LocalCoordsM;
import com.galacticodyssey.core.coords.PlanetCoordsKM;
import com.galacticodyssey.planet.BiomeMap;
import java.util.ArrayList;
import java.util.List;

public final class TerrainQuadtree implements Disposable {
    private final TerrainChunk[] roots;
    private final double radiusKm;
    private PlanetCoordsKM originPlanetKm;
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;
    private final btDiscreteDynamicsWorld dynamicsWorld;
    private final Matrix4 tmpMat = new Matrix4();

    public TerrainQuadtree(double radiusKm, PlanetCoordsKM originPlanetKm, TerrainNoiseStack noise,
                           BiomeMap biomeMap, btDiscreteDynamicsWorld dynamicsWorld) {
        this.radiusKm = radiusKm;
        this.originPlanetKm = originPlanetKm;
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.dynamicsWorld = dynamicsWorld;
        this.roots = new TerrainChunk[6];
        CubeFace[] faces = CubeFace.values();
        for (int i = 0; i < 6; i++) roots[i] = new TerrainChunk(faces[i], 0, 0f, 0f, 1f, 1f, radiusKm);
    }

    public void update(PlanetCoordsKM cameraKm) {
        for (TerrainChunk root : roots) recursiveUpdate(root, cameraKm);
    }

    private void recursiveUpdate(TerrainChunk chunk, PlanetCoordsKM cameraKm) {
        if (!chunk.meshReady) {
            generateMesh(chunk);
        }

        if (chunk.shouldSplit(cameraKm) && !chunk.hasChildren()) {
            split(chunk);
        } else if (chunk.hasChildren() && chunk.shouldMerge(cameraKm)) {
            merge(chunk);
        }

        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) recursiveUpdate(child, cameraKm);
        }
    }

    private void split(TerrainChunk chunk) {
        chunk.disposeCollision(dynamicsWorld);
        if (chunk.mesh != null) { chunk.mesh.dispose(); chunk.mesh = null; }
        chunk.meshReady = false;

        float mu = (chunk.u0 + chunk.u1) * 0.5f;
        float mv = (chunk.v0 + chunk.v1) * 0.5f;
        int d = chunk.depth + 1;
        chunk.children = new TerrainChunk[] {
            new TerrainChunk(chunk.face, d, chunk.u0, chunk.v0, mu, mv, radiusKm),
            new TerrainChunk(chunk.face, d, mu, chunk.v0, chunk.u1, mv, radiusKm),
            new TerrainChunk(chunk.face, d, chunk.u0, mv, mu, chunk.v1, radiusKm),
            new TerrainChunk(chunk.face, d, mu, mv, chunk.u1, chunk.v1, radiusKm),
        };
    }

    private void merge(TerrainChunk chunk) {
        if (chunk.children != null) {
            for (TerrainChunk child : chunk.children) {
                child.disposeCollision(dynamicsWorld);
                child.dispose();
            }
            chunk.children = null;
        }
        chunk.meshReady = false;
    }

    /** Recompute every live chunk's local placement after a floating-origin rebase. */
    public void setOrigin(PlanetCoordsKM newOrigin) {
        this.originPlanetKm = newOrigin;
        for (TerrainChunk root : roots) replaceRecursive(root);
    }

    private void replaceRecursive(TerrainChunk chunk) {
        applyPlacement(chunk);
        if (chunk.hasChildren()) for (TerrainChunk child : chunk.children) replaceRecursive(child);
    }

    private void applyPlacement(TerrainChunk chunk) {
        chunk.placementLocal = CoordConvert.planetToLocal(chunk.centerPlanetKm, originPlanetKm);
        if (chunk.collisionBody != null) {
            tmpMat.idt().setTranslation(chunk.placementLocal.x(), chunk.placementLocal.y(), chunk.placementLocal.z());
            chunk.collisionBody.setWorldTransform(tmpMat);
        }
    }

    private void generateMesh(TerrainChunk chunk) {
        TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biomeMap, radiusKm, chunk.centerPlanetKm, chunk.depth, null);

        try {
            chunk.mesh = new Mesh(true,
                TerrainMeshBuilder.GRID_SIZE * TerrainMeshBuilder.GRID_SIZE,
                data.indices.length,
                new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal"),
                new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, "a_color"));
            chunk.mesh.setVertices(data.vertices);
            chunk.mesh.setIndices(data.indices);
        } catch (Exception e) {
            chunk.mesh = null; // headless / no GL
        }

        buildCollision(chunk, data);
        applyPlacement(chunk);
        chunk.meshReady = true;
    }

    private void buildCollision(TerrainChunk chunk, TerrainMeshBuilder.MeshData data) {
        btTriangleMesh triMesh = new btTriangleMesh();
        Vector3 v0 = new Vector3(), v1 = new Vector3(), v2 = new Vector3();
        int stride = TerrainMeshBuilder.VERTEX_STRIDE;

        for (int i = 0; i < data.indices.length; i += 3) {
            int i0 = (data.indices[i] & 0xFFFF) * stride;
            int i1 = (data.indices[i + 1] & 0xFFFF) * stride;
            int i2 = (data.indices[i + 2] & 0xFFFF) * stride;

            // Chunk vertices are already chunk-local metres; placement is set via setWorldTransform.
            v0.set(data.vertices[i0], data.vertices[i0 + 1], data.vertices[i0 + 2]);
            v1.set(data.vertices[i1], data.vertices[i1 + 1], data.vertices[i1 + 2]);
            v2.set(data.vertices[i2], data.vertices[i2 + 1], data.vertices[i2 + 2]);

            triMesh.addTriangle(v0, v1, v2);
        }

        btBvhTriangleMeshShape shape = null;
        btRigidBody body = null;
        try {
            // Shape does not take ownership of triMesh — both must be disposed separately
            shape = new btBvhTriangleMeshShape(triMesh, true);
            btRigidBody.btRigidBodyConstructionInfo info =
                new btRigidBody.btRigidBodyConstructionInfo(0f, null, shape);
            try {
                body = new btRigidBody(info);
            } finally {
                info.dispose();
            }
            body.setFriction(0.9f);

            if (dynamicsWorld != null) {
                dynamicsWorld.addRigidBody(body);
            }

            chunk.triangleMesh = triMesh;
            chunk.collisionShape = shape;
            chunk.collisionBody = body;
        } catch (Exception e) {
            if (body != null) body.dispose();
            if (shape != null) shape.dispose();
            triMesh.dispose();
            throw e;
        }
    }

    private final List<TerrainChunk> leafCache = new ArrayList<>();

    public List<TerrainChunk> getVisibleLeaves() {
        leafCache.clear();
        for (TerrainChunk root : roots) collectLeaves(root, leafCache);
        return leafCache;
    }

    private void collectLeaves(TerrainChunk chunk, List<TerrainChunk> out) {
        if (!chunk.hasChildren()) { out.add(chunk); return; }
        for (TerrainChunk child : chunk.children) collectLeaves(child, out);
    }

    public void dispose(btDiscreteDynamicsWorld world) {
        for (TerrainChunk root : roots) {
            disposeRecursive(root, world);
        }
    }

    private void disposeRecursive(TerrainChunk chunk, btDiscreteDynamicsWorld world) {
        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) disposeRecursive(child, world);
        }
        chunk.disposeCollision(world);
        if (chunk.mesh != null) { chunk.mesh.dispose(); chunk.mesh = null; }
    }

    @Override
    public void dispose() {
        dispose(dynamicsWorld);
    }
}
