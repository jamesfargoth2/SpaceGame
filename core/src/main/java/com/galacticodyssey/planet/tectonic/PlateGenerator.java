package com.galacticodyssey.planet.tectonic;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.galaxy.SeedDeriver;
import com.galacticodyssey.planet.Planet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Builds a deterministic {@link TectonicModel} for a planet from the TECTONIC_DOMAIN seed. */
public final class PlateGenerator {

    public TectonicModel generate(Planet planet) {
        return generate(planet, TectonicConfig.defaults());
    }

    public TectonicModel generate(Planet planet, TectonicConfig config) {
        long tectonicSeed = SeedDeriver.domain(planet.seed, SeedDeriver.TECTONIC_DOMAIN);
        Random rng = new Random(tectonicSeed);

        int count = config.plateCountMin
                + Math.round(planet.radius * config.plateCountPerRadius)
                + rng.nextInt(Math.max(1, config.plateCountMax - config.plateCountMin + 1));

        List<Vector3> centers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) centers.add(randomUnit(rng));
        lloydRelax(centers, config.lloydIterations);

        float continentalTarget = config.continentalFractionTarget(planet.type);
        List<Plate> plates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean oceanic = rng.nextFloat() >= continentalTarget;
            float base = oceanic ? config.oceanicDepth : config.continentalBase;
            Vector3 pole = randomUnit(rng);
            float speed = 0.3f + rng.nextFloat() * 0.7f;
            plates.add(new Plate(i, centers.get(i), oceanic, base, pole, speed));
        }

        int hotspotCount = config.hotspotMin + rng.nextInt(Math.max(1, config.hotspotMax - config.hotspotMin + 1));
        List<Vector3> hotspots = new ArrayList<>(hotspotCount);
        for (int i = 0; i < hotspotCount; i++) hotspots.add(randomUnit(rng));

        return new TectonicModel(plates, hotspots, config);
    }

    /** Uniform random unit vector via normalized Gaussian triple. */
    private static Vector3 randomUnit(Random rng) {
        float x = (float) rng.nextGaussian();
        float y = (float) rng.nextGaussian();
        float z = (float) rng.nextGaussian();
        Vector3 v = new Vector3(x, y, z);
        if (v.len2() < 1e-12f) v.set(0, 1, 0);
        return v.nor();
    }

    /** Lloyd relaxation: move each center toward the average of the sphere samples nearest to it. */
    private static void lloydRelax(List<Vector3> centers, int iterations) {
        if (iterations <= 0 || centers.size() < 2) return;
        int samples = 2000;
        for (int it = 0; it < iterations; it++) {
            Vector3[] accum = new Vector3[centers.size()];
            int[] counts = new int[centers.size()];
            for (int i = 0; i < accum.length; i++) accum[i] = new Vector3();
            Vector3 d = new Vector3();
            for (int s = 0; s < samples; s++) {
                fibSphere(s, samples, d);
                int best = 0; float bestDot = -2f;
                for (int i = 0; i < centers.size(); i++) {
                    float dot = d.dot(centers.get(i));
                    if (dot > bestDot) { bestDot = dot; best = i; }
                }
                accum[best].add(d);
                counts[best]++;
            }
            for (int i = 0; i < centers.size(); i++) {
                if (counts[i] > 0 && accum[i].len2() > 1e-12f) {
                    centers.set(i, accum[i].nor());
                }
            }
        }
    }

    private static void fibSphere(int i, int n, Vector3 out) {
        float ga = MathUtils.PI * (3f - (float) Math.sqrt(5.0));
        float y = 1f - 2f * (i + 0.5f) / n;
        float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        float theta = ga * i;
        out.set(r * MathUtils.cos(theta), y, r * MathUtils.sin(theta)).nor();
    }
}
