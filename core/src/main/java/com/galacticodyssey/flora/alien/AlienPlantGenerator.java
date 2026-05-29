package com.galacticodyssey.flora.alien;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.data.TerrainGenerator;
import com.galacticodyssey.data.WorldPopulator.PopulatedWorld;
import com.galacticodyssey.flora.data.BiomePalette;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.BiomeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Alien-plant placement (pure) + prototype/instance building (GL, added in Task 6). */
public final class AlienPlantGenerator {
    static final long ALIEN_SALT = 0x616C69656EL; // "alien"
    private AlienPlantGenerator() {}

    public static List<AlienPlantPlacement> planPlacements(
            AlienPlantRegistry registry, BiomeType[] biomeGrid, float[] heightmap,
            int vertsX, int vertsZ, float worldWidth, float worldDepth,
            float seaLevel, long planetSeed, int attempts) {
        List<AlienPlantPlacement> out = new ArrayList<>();
        long seed = SeedDeriver.forId(SeedDeriver.floraDomain(planetSeed), ALIEN_SALT);
        Random rng = new Random(seed);
        float halfW = worldWidth / 2f, halfD = worldDepth / 2f;
        for (int i = 0; i < attempts; i++) {
            float wx = (rng.nextFloat() - 0.5f) * worldWidth * 0.9f;
            float wz = (rng.nextFloat() - 0.5f) * worldDepth * 0.9f;
            float h = TerrainGenerator.getHeightAt(heightmap, vertsX, vertsZ, worldWidth, worldDepth, wx, wz);
            if (h < seaLevel + 0.5f) continue;
            int gx = clamp((int) ((wx + halfW) / worldWidth * (vertsX - 1)), 0, vertsX - 1);
            int gz = clamp((int) ((wz + halfD) / worldDepth * (vertsZ - 1)), 0, vertsZ - 1);
            BiomePalette palette = registry.palette(biomeGrid[gz * vertsX + gx]);
            if (palette == null || palette.isEmpty()) continue;
            if (rng.nextFloat() > palette.density) continue;
            String speciesId = palette.pickSpecies(rng);
            if (speciesId == null) continue;
            AlienPlantSpecies sp = registry.species(speciesId);
            if (sp == null) continue;
            int variant = rng.nextInt(Math.max(1, sp.prototypeVariants));
            float yaw = rng.nextFloat() * 360f;
            float scale = 0.85f + rng.nextFloat() * 0.4f;
            out.add(new AlienPlantPlacement(speciesId, variant, wx, h, wz, yaw, scale));
        }
        return out;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Builds N prototype Models per species (GL). */
    public static Map<String, Model[]> buildPrototypes(AlienPlantRegistry registry, long planetSeed) {
        long seed = SeedDeriver.forId(SeedDeriver.floraDomain(planetSeed), ALIEN_SALT);
        Map<String, Model[]> pool = new HashMap<>();
        for (AlienPlantSpecies sp : registry.allSpecies()) {
            int variants = Math.max(1, sp.prototypeVariants);
            Model[] models = new Model[variants];
            for (int vi = 0; vi < variants; vi++) {
                long vseed = SeedDeriver.forId(seed, ((long) sp.id.hashCode() << 20) ^ vi);
                models[vi] = AlienPlantModelFactory.toModel(AlienPlantMeshBuilder.build(sp, new Random(vseed)));
            }
            pool.put(sp.id, models);
        }
        return pool;
    }

    /** Builds alien-plant ModelInstances into world.alienInstances; registers prototypes for disposal. */
    public static void populate(PopulatedWorld world, AlienPlantRegistry registry, float[] heightmap,
                                int vertsX, int vertsZ, float worldWidth, float worldDepth,
                                float seaLevel, long planetSeed) {
        Map<String, Model[]> prototypes = buildPrototypes(registry, planetSeed);
        for (Model[] arr : prototypes.values()) for (Model m : arr) world.addModel(m);
        List<AlienPlantPlacement> placements = planPlacements(
            registry, world.biomeGrid, heightmap, vertsX, vertsZ, worldWidth, worldDepth, seaLevel, planetSeed, 300);
        for (AlienPlantPlacement pl : placements) {
            Model[] variants = prototypes.get(pl.speciesId);
            if (variants == null || variants.length == 0) continue;
            ModelInstance inst = new ModelInstance(variants[pl.variantIndex % variants.length]);
            inst.transform.setToTranslation(pl.x, pl.y, pl.z);
            inst.transform.rotate(Vector3.Y, pl.yawDeg);
            inst.transform.scale(pl.scale, pl.scale, pl.scale);
            world.alienInstances.add(inst);
        }
    }
}
