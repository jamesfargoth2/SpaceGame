package com.galacticodyssey.planet;

/**
 * Carves crater profiles into a 1D heightmap array. Supports parabolic bowls
 * for SIMPLE craters, flat-floor terraced walls with central peaks for COMPLEX
 * craters, and broad shallow basins for MULTI_RING craters.
 */
public final class CraterCarver {

    private CraterCarver() {}

    /** Default erosion rate per Gyr; can be scaled by planet type. */
    private static final float DEFAULT_EROSION_RATE = 0.5f;

    /**
     * Carves a single crater into the heightmap.
     *
     * @param heights    1D heightmap array (vertsX * vertsZ)
     * @param vertsX     number of vertices along X
     * @param vertsZ     number of vertices along Z
     * @param cx         center X in vertex coordinates
     * @param cz         center Z in vertex coordinates
     * @param rimR       rim radius in vertex units
     * @param spec       crater specification
     * @param heightScale scale factor converting km to heightmap units
     */
    public static void carve(float[] heights, int vertsX, int vertsZ,
                             int cx, int cz, float rimR, CraterSpec spec,
                             float heightScale) {
        carve(heights, vertsX, vertsZ, cx, cz, rimR, spec, heightScale, DEFAULT_EROSION_RATE);
    }

    /**
     * Carves a single crater into the heightmap with a specified erosion rate.
     */
    public static void carve(float[] heights, int vertsX, int vertsZ,
                             int cx, int cz, float rimR, CraterSpec spec,
                             float heightScale, float erosionRate) {
        float bowlDegradation = (float) Math.exp(-spec.ageGyr * erosionRate * 0.8f);
        float rimDegradation = (float) Math.exp(-spec.ageGyr * erosionRate * 1.5f);
        float ejectaDegradation = (float) Math.exp(-spec.ageGyr * erosionRate * 2.0f);
        float depth = spec.depthKm * heightScale * bowlDegradation;
        float rimUp = spec.rimHeightKm * heightScale * rimDegradation;
        float peakH = spec.centralPeakHeightKm * heightScale * bowlDegradation;

        // Ejecta radius in vertex units: proportional to spec ratio
        float ejectaR = rimR * (spec.ejectaRadiusKm / (spec.diameterKm * 0.5f));
        int maxR = (int) Math.ceil(ejectaR) + 1;

        int xMin = Math.max(0, cx - maxR);
        int xMax = Math.min(vertsX - 1, cx + maxR);
        int zMin = Math.max(0, cz - maxR);
        int zMax = Math.min(vertsZ - 1, cz + maxR);

        for (int z = zMin; z <= zMax; z++) {
            for (int x = xMin; x <= xMax; x++) {
                float dx = x - cx;
                float dz = z - cz;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);
                float r = dist / rimR; // normalized distance (1.0 = rim)

                int idx = z * vertsX + x;
                float delta = 0f;

                if (r <= 1.0f) {
                    // Inside the crater bowl
                    delta = computeBowlProfile(r, depth, spec.morphology);

                    // Central peak for COMPLEX craters
                    if (spec.morphology == CraterMorphology.COMPLEX && peakH > 0f) {
                        float peakRadius = 0.2f; // peak occupies inner 20% of crater
                        if (r < peakRadius) {
                            float pr = r / peakRadius;
                            // Gaussian-shaped central peak
                            float peak = peakH * (float) Math.exp(-pr * pr * 3.0f);
                            delta += peak;
                        }
                    }

                    // Rim uplift (smoothly transitions at rim edge)
                    float rimFactor = r * r * r * r; // steeper near rim
                    delta += rimUp * rimFactor;
                } else {
                    // Outside the crater: rim uplift with exponential falloff
                    float beyondRim = r - 1.0f;
                    float rimDecay = rimUp * (float) Math.exp(-beyondRim * 3.0f);
                    delta = rimDecay;

                    // Ejecta blanket
                    float ejectaNorm = dist / ejectaR;
                    if (ejectaNorm <= 1.0f) {
                        float ejectaThickness = spec.depthKm * heightScale * ejectaDegradation
                                * 0.05f * (1.0f - ejectaNorm);
                        delta += ejectaThickness;
                    }
                }

                heights[idx] += delta;
            }
        }
    }

    private static float computeBowlProfile(float r, float depth, CraterMorphology morphology) {
        switch (morphology) {
            case SIMPLE:
                // Parabolic bowl
                return -depth * (1.0f - r * r);
            case COMPLEX:
                // Flat floor in inner 60%, terraced walls above
                if (r < 0.6f) {
                    return -depth;
                } else {
                    float wallR = (r - 0.6f) / 0.4f; // 0..1 across wall
                    // Terraced wall: step-like profile
                    float smooth = wallR * wallR;
                    float step = (float) Math.floor(wallR * 3f) / 3f;
                    float terraced = smooth * 0.6f + step * 0.4f;
                    return -depth * (1.0f - terraced);
                }
            case MULTI_RING:
                // Broad shallow basin with subtle ring structure
                float base = -depth * (1.0f - r * r);
                float ring = depth * 0.1f * (float) Math.sin(r * Math.PI * 3.0);
                return base + ring;
            default:
                return 0f;
        }
    }
}
