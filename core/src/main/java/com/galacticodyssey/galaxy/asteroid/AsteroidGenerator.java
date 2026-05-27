package com.galacticodyssey.galaxy.asteroid;

import com.galacticodyssey.galaxy.RngUtil;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.galaxy.SimplexNoise;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedurally generates asteroid meshes with deformations, craters, and mineral veins.
 */
public final class AsteroidGenerator {

    /** Mineral palettes per asteroid type. */
    private static final MineralType[][] TYPE_PALETTES = {
        /* C_TYPE */         { MineralType.CARBON, MineralType.SILICATE, MineralType.WATER_ICE },
        /* S_TYPE */         { MineralType.SILICATE, MineralType.OLIVINE, MineralType.IRON_NICKEL },
        /* M_TYPE */         { MineralType.IRON_NICKEL, MineralType.PLATINUM_GROUP, MineralType.COBALT },
        /* CONTACT_BINARY */ { MineralType.SILICATE, MineralType.IRON_NICKEL, MineralType.RARE_EARTH }
    };

    public GeneratedAsteroid generate(AsteroidConfig cfg) {
        long terrainSeed = SeedDeriver.domain(cfg.seed, SeedDeriver.TERRAIN_DOMAIN);
        Random rng = new Random(terrainSeed);
        SimplexNoise lowFreq = new SimplexNoise(terrainSeed);
        SimplexNoise medFreq = new SimplexNoise(terrainSeed + 1);
        SimplexNoise highFreq = new SimplexNoise(terrainSeed + 2);

        // 1) Create icosphere
        int subdivisions = subdivisionLevel(cfg.radiusKm);
        IcosphereResult ico = createIcosphere(subdivisions);
        float[] verts = ico.vertices;
        int[] indices = ico.indices;

        // 2) Elongation along random axis
        float elongation = RngUtil.range(rng, 1.2f, 2.5f);
        float axisX = rng.nextFloat() - 0.5f;
        float axisY = rng.nextFloat() - 0.5f;
        float axisZ = rng.nextFloat() - 0.5f;
        float axisLen = (float) Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (axisLen > 0.001f) {
            axisX /= axisLen;
            axisY /= axisLen;
            axisZ /= axisLen;
        } else {
            axisX = 1f; axisY = 0f; axisZ = 0f;
        }
        applyElongation(verts, axisX, axisY, axisZ, elongation);

        // 3) Lobe deformation (low-freq noise)
        applyNoise(verts, lowFreq, 0.5f, cfg.type.lobeFactor, cfg.radiusKm);

        // 4) Bump deformation (med-freq noise)
        applyNoise(verts, medFreq, 2.0f, cfg.type.bumpFactor, cfg.radiusKm);

        // 5) Roughness (high-freq noise)
        applyNoise(verts, highFreq, 8.0f, cfg.type.roughnessFactor, cfg.radiusKm);

        // 6) Flat face carving
        for (int f = 0; f < cfg.flatCount; f++) {
            float nx = rng.nextFloat() - 0.5f;
            float ny = rng.nextFloat() - 0.5f;
            float nz = rng.nextFloat() - 0.5f;
            float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLen < 0.001f) continue;
            nx /= nLen; ny /= nLen; nz /= nLen;
            float cutDepth = RngUtil.range(rng, 0.6f, 0.85f) * cfg.radiusKm;
            carveFlatFace(verts, nx, ny, nz, cutDepth);
        }

        // 7) Crater imprinting
        for (int c = 0; c < cfg.craterCount; c++) {
            float cx = rng.nextFloat() - 0.5f;
            float cy = rng.nextFloat() - 0.5f;
            float cz = rng.nextFloat() - 0.5f;
            float cLen = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (cLen < 0.001f) continue;
            cx /= cLen; cy /= cLen; cz /= cLen;
            float craterRadius = RngUtil.range(rng, 0.05f, 0.25f) * cfg.radiusKm;
            float craterDepth = craterRadius * RngUtil.range(rng, 0.1f, 0.4f);
            imprintCrater(verts, cx, cy, cz, cfg.radiusKm, craterRadius, craterDepth);
        }

        // Scale to actual radius
        scaleVertices(verts, cfg.radiusKm);

        // 8) Mineral vein placement
        MineralType[] palette = TYPE_PALETTES[cfg.type.ordinal()];
        List<VeinDeposit> veins = new ArrayList<>();
        for (int v = 0; v < cfg.veinCount; v++) {
            float vx = (rng.nextFloat() - 0.5f) * cfg.radiusKm * 1.5f;
            float vy = (rng.nextFloat() - 0.5f) * cfg.radiusKm * 1.5f;
            float vz = (rng.nextFloat() - 0.5f) * cfg.radiusKm * 1.5f;
            MineralType mineral = palette[rng.nextInt(palette.length)];
            float veinRadius = RngUtil.range(rng, 0.01f, 0.15f) * cfg.radiusKm;
            float richness = RngUtil.range(rng, 0.1f, 1.0f);
            veins.add(new VeinDeposit(vx, vy, vz, mineral, veinRadius, richness));
        }

        return new GeneratedAsteroid(verts, indices, veins, cfg);
    }

    private int subdivisionLevel(float radiusKm) {
        if (radiusKm < 0.1f) return 1;
        if (radiusKm < 1f) return 2;
        if (radiusKm < 10f) return 3;
        return 4;
    }

    /**
     * Applies elongation along a given axis direction.
     */
    private void applyElongation(float[] verts, float ax, float ay, float az, float ratio) {
        for (int i = 0; i < verts.length; i += 3) {
            float dot = verts[i] * ax + verts[i + 1] * ay + verts[i + 2] * az;
            verts[i] += ax * dot * (ratio - 1f);
            verts[i + 1] += ay * dot * (ratio - 1f);
            verts[i + 2] += az * dot * (ratio - 1f);
        }
    }

    /**
     * Applies simplex noise displacement along vertex normal direction.
     */
    private void applyNoise(float[] verts, SimplexNoise noise, float frequency,
                            float factor, float radius) {
        if (factor < 0.0001f) return;
        for (int i = 0; i < verts.length; i += 3) {
            float x = verts[i];
            float y = verts[i + 1];
            float z = verts[i + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 0.0001f) continue;
            float nx = x / len;
            float ny = y / len;

            // Use 2D simplex with spherical coordinates for 3D effect
            float theta = (float) Math.atan2(ny, nx);
            float phi = (float) Math.acos(Math.max(-1f, Math.min(1f, z / len)));
            float n = noise.noise(theta * frequency, phi * frequency);
            float displacement = n * factor * radius;

            verts[i] += (x / len) * displacement;
            verts[i + 1] += (y / len) * displacement;
            verts[i + 2] += (z / len) * displacement;
        }
    }

    /**
     * Carves a flat face by cutting vertices along a plane.
     */
    private void carveFlatFace(float[] verts, float nx, float ny, float nz, float cutDepth) {
        for (int i = 0; i < verts.length; i += 3) {
            float dot = verts[i] * nx + verts[i + 1] * ny + verts[i + 2] * nz;
            if (dot > cutDepth) {
                float excess = dot - cutDepth;
                verts[i] -= nx * excess;
                verts[i + 1] -= ny * excess;
                verts[i + 2] -= nz * excess;
            }
        }
    }

    /**
     * Imprints a crater centered on a direction on the asteroid surface.
     * Uses angular distance from crater center to determine depression depth.
     */
    private void imprintCrater(float[] verts, float cx, float cy, float cz,
                               float asteroidRadius, float craterRadius, float craterDepth) {
        float angularRadius = craterRadius / asteroidRadius;
        for (int i = 0; i < verts.length; i += 3) {
            float x = verts[i];
            float y = verts[i + 1];
            float z = verts[i + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len < 0.0001f) continue;
            float vx = x / len;
            float vy = y / len;
            float vz = z / len;
            float dot = vx * cx + vy * cy + vz * cz;
            float angle = (float) Math.acos(Math.max(-1f, Math.min(1f, dot)));
            if (angle < angularRadius) {
                float t = angle / angularRadius;
                float depression = craterDepth * (1f - t * t);
                verts[i] -= vx * depression;
                verts[i + 1] -= vy * depression;
                verts[i + 2] -= vz * depression;
            }
        }
    }

    /**
     * Scales all vertices by the given radius.
     */
    private void scaleVertices(float[] verts, float radius) {
        for (int i = 0; i < verts.length; i++) {
            verts[i] *= radius;
        }
    }

    // ---- Icosphere generation ----

    private static final class IcosphereResult {
        final float[] vertices;
        final int[] indices;

        IcosphereResult(float[] vertices, int[] indices) {
            this.vertices = vertices;
            this.indices = indices;
        }
    }

    private IcosphereResult createIcosphere(int subdivisions) {
        // Start with icosahedron
        float t = (1f + (float) Math.sqrt(5f)) / 2f;

        List<float[]> vertexList = new ArrayList<>();
        vertexList.add(normalize(new float[]{-1, t, 0}));
        vertexList.add(normalize(new float[]{1, t, 0}));
        vertexList.add(normalize(new float[]{-1, -t, 0}));
        vertexList.add(normalize(new float[]{1, -t, 0}));
        vertexList.add(normalize(new float[]{0, -1, t}));
        vertexList.add(normalize(new float[]{0, 1, t}));
        vertexList.add(normalize(new float[]{0, -1, -t}));
        vertexList.add(normalize(new float[]{0, 1, -t}));
        vertexList.add(normalize(new float[]{t, 0, -1}));
        vertexList.add(normalize(new float[]{t, 0, 1}));
        vertexList.add(normalize(new float[]{-t, 0, -1}));
        vertexList.add(normalize(new float[]{-t, 0, 1}));

        List<int[]> triangles = new ArrayList<>();
        // 5 faces around point 0
        triangles.add(new int[]{0, 11, 5});
        triangles.add(new int[]{0, 5, 1});
        triangles.add(new int[]{0, 1, 7});
        triangles.add(new int[]{0, 7, 10});
        triangles.add(new int[]{0, 10, 11});
        // 5 adjacent faces
        triangles.add(new int[]{1, 5, 9});
        triangles.add(new int[]{5, 11, 4});
        triangles.add(new int[]{11, 10, 2});
        triangles.add(new int[]{10, 7, 6});
        triangles.add(new int[]{7, 1, 8});
        // 5 faces around point 3
        triangles.add(new int[]{3, 9, 4});
        triangles.add(new int[]{3, 4, 2});
        triangles.add(new int[]{3, 2, 6});
        triangles.add(new int[]{3, 6, 8});
        triangles.add(new int[]{3, 8, 9});
        // 5 adjacent faces
        triangles.add(new int[]{4, 9, 5});
        triangles.add(new int[]{2, 4, 11});
        triangles.add(new int[]{6, 2, 10});
        triangles.add(new int[]{8, 6, 7});
        triangles.add(new int[]{9, 8, 1});

        // Subdivide
        for (int s = 0; s < subdivisions; s++) {
            List<int[]> newTriangles = new ArrayList<>();
            java.util.Map<Long, Integer> midpointCache = new java.util.HashMap<>();
            for (int[] tri : triangles) {
                int a = getMidpoint(vertexList, midpointCache, tri[0], tri[1]);
                int b = getMidpoint(vertexList, midpointCache, tri[1], tri[2]);
                int c = getMidpoint(vertexList, midpointCache, tri[2], tri[0]);
                newTriangles.add(new int[]{tri[0], a, c});
                newTriangles.add(new int[]{tri[1], b, a});
                newTriangles.add(new int[]{tri[2], c, b});
                newTriangles.add(new int[]{a, b, c});
            }
            triangles = newTriangles;
        }

        // Convert to arrays
        float[] vertices = new float[vertexList.size() * 3];
        for (int i = 0; i < vertexList.size(); i++) {
            float[] v = vertexList.get(i);
            vertices[i * 3] = v[0];
            vertices[i * 3 + 1] = v[1];
            vertices[i * 3 + 2] = v[2];
        }
        int[] indices = new int[triangles.size() * 3];
        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            indices[i * 3] = tri[0];
            indices[i * 3 + 1] = tri[1];
            indices[i * 3 + 2] = tri[2];
        }
        return new IcosphereResult(vertices, indices);
    }

    private int getMidpoint(List<float[]> vertices, java.util.Map<Long, Integer> cache,
                            int v1, int v2) {
        long smallerIndex = Math.min(v1, v2);
        long greaterIndex = Math.max(v1, v2);
        long key = (smallerIndex << 32) + greaterIndex;

        Integer cached = cache.get(key);
        if (cached != null) return cached;

        float[] p1 = vertices.get(v1);
        float[] p2 = vertices.get(v2);
        float[] mid = normalize(new float[]{
            (p1[0] + p2[0]) / 2f,
            (p1[1] + p2[1]) / 2f,
            (p1[2] + p2[2]) / 2f
        });
        int index = vertices.size();
        vertices.add(mid);
        cache.put(key, index);
        return index;
    }

    private float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
