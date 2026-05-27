package com.galacticodyssey.planet.rings;

import com.badlogic.gdx.utils.Array;

public final class ResonanceGapCalculator {

    public static final int[][] STANDARD_RESONANCES = {
        {2,1},{3,2},{4,3},{5,4},{5,3},{7,4},{7,6}
    };

    /** Returns orbital radii (km from planet centre) where resonance gaps form given a perturbing moon orbit. */
    public Array<Float> gapRadii(float moonOrbitKm, int[][] ratios) {
        Array<Float> gaps = new Array<>();
        for (int[] ratio : ratios) {
            // r_gap = moonOrbit * (n_inner / n_outer)^(2/3)
            float r = moonOrbitKm * (float) Math.pow((double) ratio[0] / ratio[1], 2.0 / 3.0);
            gaps.add(r);
        }
        return gaps;
    }
}
