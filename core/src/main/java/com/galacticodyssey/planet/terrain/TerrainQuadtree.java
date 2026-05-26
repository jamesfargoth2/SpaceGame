package com.galacticodyssey.planet.terrain;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.galacticodyssey.planet.BiomeMap;
import java.util.ArrayList;
import java.util.List;

public final class TerrainQuadtree implements Disposable {
    private final TerrainChunk[] roots;
    private final float planetRadius;
    private final TerrainNoiseStack noise;
    private final BiomeMap biomeMap;

    public TerrainQuadtree(float planetRadius, TerrainNoiseStack noise, BiomeMap biomeMap) {
        this.planetRadius = planetRadius;
        this.noise = noise;
        this.biomeMap = biomeMap;
        this.roots = new TerrainChunk[6];
        CubeFace[] faces = CubeFace.values();
        for (int i = 0; i < 6; i++) {
            roots[i] = new TerrainChunk(faces[i], 0, 0f, 0f, 1f, 1f, planetRadius);
        }
    }

    public void update(Vector3 cameraPos) {
        for (TerrainChunk root : roots) {
            recursiveUpdate(root, cameraPos);
        }
    }

    private void recursiveUpdate(TerrainChunk chunk, Vector3 cameraPos) {
        if (!chunk.meshReady) {
            generateMesh(chunk);
        }

        if (chunk.shouldSplit(cameraPos) && !chunk.hasChildren()) {
            split(chunk);
        } else if (chunk.hasChildren() && chunk.shouldMerge(cameraPos)) {
            merge(chunk);
        }

        if (chunk.hasChildren()) {
            for (TerrainChunk child : chunk.children) {
                recursiveUpdate(child, cameraPos);
            }
        }
    }

    private void split(TerrainChunk chunk) {
        float mu = (chunk.u0 + chunk.u1) * 0.5f;
        float mv = (chunk.v0 + chunk.v1) * 0.5f;
        int d = chunk.depth + 1;
        chunk.children = new TerrainChunk[] {
            new TerrainChunk(chunk.face, d, chunk.u0, chunk.v0, mu, mv, planetRadius),
            new TerrainChunk(chunk.face, d, mu, chunk.v0, chunk.u1, mv, planetRadius),
            new TerrainChunk(chunk.face, d, chunk.u0, mv, mu, chunk.v1, planetRadius),
            new TerrainChunk(chunk.face, d, mu, mv, chunk.u1, chunk.v1, planetRadius),
        };
    }

    private void merge(TerrainChunk chunk) {
        if (chunk.children != null) {
            for (TerrainChunk child : chunk.children) child.dispose();
            chunk.children = null;
        }
    }

    private void generateMesh(TerrainChunk chunk) {
        TerrainMeshBuilder.MeshData data = TerrainMeshBuilder.build(
            chunk.face, chunk.u0, chunk.v0, chunk.u1, chunk.v1,
            noise, biomeMap, planetRadius, chunk.depth, null);
        chunk.meshReady = true;
    }

    public List<TerrainChunk> getVisibleLeaves() {
        List<TerrainChunk> leaves = new ArrayList<>();
        for (TerrainChunk root : roots) collectLeaves(root, leaves);
        return leaves;
    }

    private void collectLeaves(TerrainChunk chunk, List<TerrainChunk> out) {
        if (!chunk.hasChildren()) { out.add(chunk); return; }
        for (TerrainChunk child : chunk.children) collectLeaves(child, out);
    }

    @Override
    public void dispose() {
        for (TerrainChunk root : roots) root.dispose();
    }
}
