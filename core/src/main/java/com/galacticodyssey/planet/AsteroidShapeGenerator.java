package com.galacticodyssey.planet;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.galaxy.SimplexNoise;

import java.util.*;

public final class AsteroidShapeGenerator {

    private static final int SUBDIVISIONS = 2;

    public AsteroidShape generate(long seed, float baseRadius, AsteroidComposition composition) {
        long shapeSeed = SeedDeriver.forId(
            SeedDeriver.domain(seed, SeedDeriver.ASTEROID_SHAPE_DOMAIN), 0);
        Random rng = new Random(shapeSeed);
        SimplexNoise simplex = new SimplexNoise(shapeSeed);

        float offsetX = rng.nextFloat() * 1000f;
        float offsetY = rng.nextFloat() * 1000f;
        float offsetZ = rng.nextFloat() * 1000f;

        // Generate icosphere vertices and faces
        List<float[]> vertList = new ArrayList<>();
        List<int[]> faceList = new ArrayList<>();
        generateIcosphere(vertList, faceList);

        int vertCount = vertList.size();
        float[] vertices = new float[vertCount * 3];
        float[] normals = new float[vertCount * 3];
        float maxRadius = 0f;

        for (int i = 0; i < vertCount; i++) {
            float[] v = vertList.get(i);
            float nx = v[0], ny = v[1], nz = v[2];

            float displacement = 0f;
            float amplitude = composition.roughness * baseRadius * 0.4f;
            float frequency = 1.0f;
            for (int octave = 0; octave < 4; octave++) {
                float sampleX = (nx + offsetX) * frequency;
                float sampleY = (ny + offsetY) * frequency;
                float sampleZ = (nz + offsetZ) * frequency;
                displacement += simplex.noise(sampleX, sampleY, sampleZ) * amplitude;
                amplitude *= 0.5f;
                frequency *= 2.0f;
            }

            float r = baseRadius + displacement;
            vertices[i * 3]     = nx * r;
            vertices[i * 3 + 1] = ny * r;
            vertices[i * 3 + 2] = nz * r;
            maxRadius = Math.max(maxRadius, r);
        }

        short[] indices = new short[faceList.size() * 3];
        for (int i = 0; i < faceList.size(); i++) {
            indices[i * 3]     = (short) faceList.get(i)[0];
            indices[i * 3 + 1] = (short) faceList.get(i)[1];
            indices[i * 3 + 2] = (short) faceList.get(i)[2];
        }

        computeNormals(vertices, indices, normals);

        return new AsteroidShape(shapeSeed, vertices, indices, normals, maxRadius, composition);
    }

    private void generateIcosphere(List<float[]> verts, List<int[]> faces) {
        float t = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] icoVerts = {
            {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
            {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
            {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1}
        };
        int[][] icoFaces = {
            {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
            {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
            {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
            {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}
        };

        for (float[] v : icoVerts) {
            float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
            verts.add(new float[]{v[0]/len, v[1]/len, v[2]/len});
        }
        for (int[] f : icoFaces) {
            faces.add(f.clone());
        }

        Map<Long, Integer> midCache = new HashMap<>();
        for (int s = 0; s < SUBDIVISIONS; s++) {
            List<int[]> newFaces = new ArrayList<>();
            for (int[] face : faces) {
                int a = getMidpoint(face[0], face[1], verts, midCache);
                int b = getMidpoint(face[1], face[2], verts, midCache);
                int c = getMidpoint(face[2], face[0], verts, midCache);
                newFaces.add(new int[]{face[0], a, c});
                newFaces.add(new int[]{face[1], b, a});
                newFaces.add(new int[]{face[2], c, b});
                newFaces.add(new int[]{a, b, c});
            }
            faces.clear();
            faces.addAll(newFaces);
        }
    }

    private int getMidpoint(int i1, int i2, List<float[]> verts, Map<Long, Integer> cache) {
        long key = Math.min(i1, i2) * 100000L + Math.max(i1, i2);
        Integer cached = cache.get(key);
        if (cached != null) return cached;

        float[] v1 = verts.get(i1);
        float[] v2 = verts.get(i2);
        float[] mid = {(v1[0]+v2[0])/2f, (v1[1]+v2[1])/2f, (v1[2]+v2[2])/2f};
        float len = (float) Math.sqrt(mid[0]*mid[0] + mid[1]*mid[1] + mid[2]*mid[2]);
        mid[0] /= len; mid[1] /= len; mid[2] /= len;

        int idx = verts.size();
        verts.add(mid);
        cache.put(key, idx);
        return idx;
    }

    private void computeNormals(float[] vertices, short[] indices, float[] normals) {
        Vector3 v0 = new Vector3(), v1 = new Vector3(), v2 = new Vector3();
        Vector3 edge1 = new Vector3(), edge2 = new Vector3(), faceNormal = new Vector3();

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i] & 0xFFFF, i1 = indices[i+1] & 0xFFFF, i2 = indices[i+2] & 0xFFFF;
            v0.set(vertices[i0*3], vertices[i0*3+1], vertices[i0*3+2]);
            v1.set(vertices[i1*3], vertices[i1*3+1], vertices[i1*3+2]);
            v2.set(vertices[i2*3], vertices[i2*3+1], vertices[i2*3+2]);

            edge1.set(v1).sub(v0);
            edge2.set(v2).sub(v0);
            faceNormal.set(edge1).crs(edge2);

            normals[i0*3] += faceNormal.x; normals[i0*3+1] += faceNormal.y; normals[i0*3+2] += faceNormal.z;
            normals[i1*3] += faceNormal.x; normals[i1*3+1] += faceNormal.y; normals[i1*3+2] += faceNormal.z;
            normals[i2*3] += faceNormal.x; normals[i2*3+1] += faceNormal.y; normals[i2*3+2] += faceNormal.z;
        }

        for (int i = 0; i < normals.length; i += 3) {
            float len = (float) Math.sqrt(normals[i]*normals[i] + normals[i+1]*normals[i+1] + normals[i+2]*normals[i+2]);
            if (len > 0) { normals[i] /= len; normals[i+1] /= len; normals[i+2] /= len; }
        }
    }
}
