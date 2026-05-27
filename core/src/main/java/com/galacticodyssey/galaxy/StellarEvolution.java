package com.galacticodyssey.galaxy;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

public final class StellarEvolution {
    private StellarEvolution() {}

    /** Main sequence lifetime: t_MS = 10 * M^(-2.5) Gyr. */
    public static float mainSequenceLifetime(float massSolar) {
        if (massSolar <= 0f) return Float.MAX_VALUE;
        return 10f * (float) Math.pow(massSolar, -2.5);
    }

    /** Returns true if the star has exhausted its main sequence lifetime. */
    public static boolean isEvolved(float massSolar, float ageGyr) {
        return ageGyr > mainSequenceLifetime(massSolar);
    }

    /** Main sequence luminosity (piecewise mass-luminosity relation). */
    public static float mainSequenceLuminosity(float massSolar) {
        if (massSolar < 0.43f) return 0.23f * (float) Math.pow(massSolar, 2.3);
        if (massSolar < 2f) return (float) Math.pow(massSolar, 4.0);
        return 1.4f * (float) Math.pow(massSolar, 3.5);
    }

    /** Evolved star luminosity based on luminosity class. */
    public static float evolvedLuminosity(float massSolar, LuminosityClass lc, Random rng) {
        float msLum = mainSequenceLuminosity(massSolar);
        return switch (lc) {
            case GIANT -> msLum * RngUtil.range(rng, 50f, 200f);
            case SUPERGIANT -> msLum * RngUtil.range(rng, 1000f, 10000f);
            case WHITE_DWARF -> 0.001f + massSolar * 0.005f;
            default -> msLum;
        };
    }

    /** Main sequence radius from mass (solar radii). */
    public static float mainSequenceRadius(float massSolar) {
        if (massSolar < 1f) return (float) Math.pow(massSolar, 0.8);
        return (float) Math.pow(massSolar, 0.57);
    }

    /** Evolved star radius based on luminosity class. */
    public static float evolvedRadius(float massSolar, LuminosityClass lc, Random rng) {
        return switch (lc) {
            case GIANT -> mainSequenceRadius(massSolar) * RngUtil.range(rng, 10f, 100f);
            case SUPERGIANT -> mainSequenceRadius(massSolar) * RngUtil.range(rng, 100f, 1500f);
            case WHITE_DWARF -> RngUtil.range(rng, 0.008f, 0.02f);
            default -> mainSequenceRadius(massSolar);
        };
    }

    /**
     * Star color from effective temperature using Wien displacement law + Tanner Helland algorithm.
     * Accurate from ~1000K to ~40000K.
     */
    public static Color colorFromTemperature(float tempK) {
        float temp = tempK / 100f;
        float r, g, b;

        // Red
        if (temp <= 66f) {
            r = 255f;
        } else {
            r = 329.698727446f * (float) Math.pow(temp - 60f, -0.1332047592f);
        }

        // Green
        if (temp <= 66f) {
            g = 99.4708025861f * (float) Math.log(temp) - 161.1195681661f;
        } else {
            g = 288.1221695283f * (float) Math.pow(temp - 60f, -0.0755148492f);
        }

        // Blue
        if (temp >= 66f) {
            b = 255f;
        } else if (temp <= 19f) {
            b = 0f;
        } else {
            b = 138.5177312231f * (float) Math.log(temp - 10f) - 305.0447927307f;
        }

        r = Math.max(0f, Math.min(255f, r)) / 255f;
        g = Math.max(0f, Math.min(255f, g)) / 255f;
        b = Math.max(0f, Math.min(255f, b)) / 255f;
        return new Color(r, g, b, 1f);
    }
}
