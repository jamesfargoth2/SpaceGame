package com.galacticodyssey.planet;

import com.galacticodyssey.galaxy.RngUtil;
import java.util.Random;

/**
 * Immutable specification for a single impact crater, including geometry,
 * age-based degradation, and morphology classification.
 */
public final class CraterSpec {
    public final float diameterKm;
    public final float depthKm;
    public final float rimHeightKm;
    public final float ejectaRadiusKm;
    public final float centralPeakHeightKm;
    public final boolean hasRaySystem;
    public final float ageGyr;
    public final CraterMorphology morphology;

    public CraterSpec(float diameterKm, float depthKm, float rimHeightKm,
                      float ejectaRadiusKm, float centralPeakHeightKm,
                      boolean hasRaySystem, float ageGyr, CraterMorphology morphology) {
        this.diameterKm = diameterKm;
        this.depthKm = depthKm;
        this.rimHeightKm = rimHeightKm;
        this.ejectaRadiusKm = ejectaRadiusKm;
        this.centralPeakHeightKm = centralPeakHeightKm;
        this.hasRaySystem = hasRaySystem;
        this.ageGyr = ageGyr;
        this.morphology = morphology;
    }

    /**
     * Creates a crater specification from diameter and age using empirical
     * scaling laws. Morphology is classified by diameter thresholds:
     * SIMPLE (&lt; 15 km), COMPLEX (15-300 km), MULTI_RING (&gt;= 300 km).
     */
    public static CraterSpec fromDiameter(float diamKm, float ageGyr, Random rng) {
        CraterMorphology morphology;
        float depth;
        float centralPeakHeight = 0f;

        if (diamKm < 15f) {
            morphology = CraterMorphology.SIMPLE;
            depth = 0.196f * (float) Math.pow(diamKm, 1.010);
        } else if (diamKm < 300f) {
            morphology = CraterMorphology.COMPLEX;
            depth = 1.044f * (float) Math.pow(diamKm, 0.301);
            centralPeakHeight = depth * RngUtil.range(rng, 0.1f, 0.3f);
        } else {
            morphology = CraterMorphology.MULTI_RING;
            depth = diamKm * 0.01f;
        }

        float rimHeight = depth * RngUtil.range(rng, 0.15f, 0.25f);
        float ejectaRadius = diamKm * RngUtil.range(rng, 1.5f, 3.0f);
        boolean hasRaySystem = ageGyr < 1.0f && rng.nextFloat() < 0.6f;

        return new CraterSpec(diamKm, depth, rimHeight, ejectaRadius,
                centralPeakHeight, hasRaySystem, ageGyr, morphology);
    }
}
