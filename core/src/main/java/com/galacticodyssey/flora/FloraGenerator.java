package com.galacticodyssey.flora;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.WorldPopulator.PopulatedWorld;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.flora.data.FloraRegistry;
import com.galacticodyssey.flora.data.FloraSpecies;
import com.galacticodyssey.flora.gen.AttractionEnvelope;
import com.galacticodyssey.flora.gen.BranchSkeleton;
import com.galacticodyssey.flora.gen.FloraMeshBuilder;
import com.galacticodyssey.flora.gen.FloraMeshData;
import com.galacticodyssey.flora.gen.FloraModelFactory;
import com.galacticodyssey.flora.gen.SpaceColonization;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Orchestrates flora: deterministic placement planning + (Task 10) prototype/instance building. */
public final class FloraGenerator {
    private FloraGenerator() {}

    /**
     * Pure, GL-free placement decision: where flora goes, what species/variant, transform.
     * Mirrors the old WorldPopulator.placeTrees scatter, but data-driven and seeded via FLORA_DOMAIN.
     */
    public static List<FloraPlacement> planPlacements(
            FloraRegistry registry, BiomeType[] biomeGrid, float[] heightmap,
            int vertsX, int vertsZ, float worldWidth, float worldDepth,
            float seaLevel, long planetSeed, int attempts) {

        List<FloraPlacement> out = new ArrayList<>();
        long floraSeed = SeedDeriver.floraDomain(planetSeed);
        Random rng = new Random(floraSeed);
        float halfW = worldWidth / 2f, halfD = worldDepth / 2f;

        for (int i = 0; i < attempts; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 0.5f) continue;

            int gx = clamp((int) ((wx + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
            int gz = clamp((int) ((wz + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
            BiomeType biome = biomeGrid[gz * vertsX + gx];

            BiomePalette palette = registry.palette(biome);
            if (palette == null || palette.isEmpty()) continue;
            if (rng.nextFloat() > palette.density) continue;

            String speciesId = palette.pickSpecies(rng);
            if (speciesId == null) continue;
            FloraSpecies sp = registry.species(speciesId);
            if (sp == null) continue;

            int variant = rng.nextInt(Math.max(1, sp.prototypeVariants));
            float yaw = rng.nextFloat() * 360f;
            float scale = 0.85f + rng.nextFloat() * 0.4f;
            out.add(new FloraPlacement(speciesId, variant, wx, h, wz, yaw, scale));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Builds N prototype Models per species (one pool per planet seed). Requires GL. */
    public static Map<String, Model[]> buildPrototypes(FloraRegistry registry, long planetSeed) {
        long floraSeed = SeedDeriver.floraDomain(planetSeed);
        Map<String, Model[]> pool = new HashMap<>();
        for (FloraSpecies sp : registry.allSpecies()) {
            int variants = Math.max(1, sp.prototypeVariants);
            Model[] models = new Model[variants];
            for (int v = 0; v < variants; v++) {
                long vseed = SeedDeriver.forId(floraSeed, ((long) sp.id.hashCode() << 20) ^ v);
                models[v] = buildOne(sp, vseed);
            }
            pool.put(sp.id, models);
        }
        return pool;
    }

    private static Model buildOne(FloraSpecies sp, long seed) {
        Random rng = new Random(seed);
        float height = sp.heightMin + rng.nextFloat() * (sp.heightMax - sp.heightMin);
        float radius = sp.radiusMin + rng.nextFloat() * (sp.radiusMax - sp.radiusMin);
        Array<Vector3> pts = AttractionEnvelope.generate(sp.shape, height, radius, sp.attractionPoints, rng);

        SpaceColonization.GrowthParams gp = new SpaceColonization.GrowthParams();
        gp.influenceRadius = sp.influenceRadius;
        gp.killDistance = sp.killDistance;
        gp.segmentLength = sp.segmentLength;
        gp.maxNodes = sp.maxNodes;
        BranchSkeleton skel = SpaceColonization.grow(pts, gp, new Random(seed ^ 0x9E3779B9L));

        FloraMeshData mesh = FloraMeshBuilder.build(skel, sp, new Random(seed ^ 0x1234ABCDL));
        Color trunk = jitter(sp.trunkColor, rng, 0.05f);
        Color foliage = lerpColor(sp.foliageColorA, sp.foliageColorB, rng.nextFloat());
        return FloraModelFactory.toModel(mesh, trunk, foliage);
    }

    /**
     * Builds flora ModelInstances into {@code world.treeInstances} and registers prototype
     * Models for disposal via {@code world.addModel}. Requires GL.
     */
    public static void populate(PopulatedWorld world, FloraRegistry registry, float[] heightmap,
                                int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                float seaLevel, long planetSeed) {
        Map<String, Model[]> prototypes = buildPrototypes(registry, planetSeed);
        for (Model[] models : prototypes.values()) {
            for (Model m : models) world.addModel(m);
        }
        List<FloraPlacement> placements = planPlacements(
            registry, world.biomeGrid, heightmap, vertsX, vertsZ, worldWidth, worldDepth,
            seaLevel, planetSeed, 300);

        for (FloraPlacement pl : placements) {
            Model[] variants = prototypes.get(pl.speciesId);
            if (variants == null || variants.length == 0) continue;
            Model proto = variants[pl.variantIndex % variants.length];
            ModelInstance inst = new ModelInstance(proto);
            inst.transform.setToTranslation(pl.x, pl.y, pl.z);
            inst.transform.rotate(Vector3.Y, pl.yawDeg);
            inst.transform.scale(pl.scale, pl.scale, pl.scale);
            world.treeInstances.add(inst);
        }
    }

    private static Color jitter(Color base, Random rng, float amt) {
        float d = (rng.nextFloat() - 0.5f) * 2f * amt;
        return new Color(clamp01(base.r + d), clamp01(base.g + d), clamp01(base.b + d), 1f);
    }

    private static Color lerpColor(Color a, Color b, float t) {
        return new Color(a.r + (b.r - a.r) * t, a.g + (b.g - a.g) * t, a.b + (b.b - a.b) * t, 1f);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
}
