package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Distributes impact craters across a planetary surface using power-law size
 * distributions and age-dependent density. Craters are returned sorted
 * largest-first so that big craters overprint small ones during carving.
 */
public final class CraterFieldGenerator {

    /** Maximum number of craters generated per planet. */
    private static final int MAX_CRATERS = 10_000;

    private CraterFieldGenerator() {}

    /**
     * Generates a list of crater specifications for a planet.
     *
     * @param planetAgeGyr    age of the planet in billions of years
     * @param surfaceAgeGyr   age of the surface (may differ due to resurfacing)
     * @param planetDiameterKm diameter of the planet in km
     * @param rng             seeded random source
     * @return craters sorted largest-first by diameterKm
     */
    public static List<CraterSpec> generate(float planetAgeGyr, float surfaceAgeGyr,
                                            float planetDiameterKm, Random rng) {
        // Surface area proportional to diameter^2 (simplified)
        float area = planetDiameterKm * planetDiameterKm;

        // Crater count scales exponentially with surface age and linearly with area
        int count = (int) (area * 0.0001f *
                (1.0f - (float) Math.exp(-surfaceAgeGyr * 1.5f)) * 100f);
        count = Math.min(count, MAX_CRATERS);
        count = Math.max(count, 0);

        float minDiam = 0.5f;
        float maxDiam = planetDiameterKm * 0.05f;

        List<CraterSpec> craters = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Power-law size distribution: many small craters, few large ones
            // Using inverse CDF of power law with exponent -2
            float u = rng.nextFloat();
            // Avoid u == 0 which would cause division issues
            u = Math.max(u, 1e-6f);
            float diam = minDiam / u;
            diam = Math.min(diam, maxDiam);
            diam = Math.max(diam, minDiam);

            // Age of individual crater is random within surface age
            float craterAge = rng.nextFloat() * surfaceAgeGyr;

            craters.add(CraterSpec.fromDiameter(diam, craterAge, rng));
        }

        // Generate secondary craters from large, young primary impacts
        List<CraterSpec> secondaries = new ArrayList<>();
        for (CraterSpec primary : craters) {
            if (primary.diameterKm > 20f && primary.ageGyr < 2.0f) {
                int secondaryCount = RngUtil.range(rng, 3, 11);
                for (int s = 0; s < secondaryCount; s++) {
                    float secondaryDiam = primary.diameterKm * RngUtil.range(rng, 0.02f, 0.08f);
                    secondaryDiam = Math.max(minDiam, secondaryDiam);
                    secondaries.add(CraterSpec.secondary(secondaryDiam, primary.ageGyr, rng));
                }
            }
        }
        int remaining = MAX_CRATERS - craters.size();
        if (remaining > 0 && !secondaries.isEmpty()) {
            if (secondaries.size() > remaining) {
                secondaries = secondaries.subList(0, remaining);
            }
            craters.addAll(secondaries);
        }

        // Sort largest first so big craters overprint small ones
        craters.sort(Comparator.comparingDouble((CraterSpec c) -> c.diameterKm).reversed());

        return craters;
    }
}
