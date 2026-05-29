package com.galacticodyssey.fauna.skin;

import com.galacticodyssey.fauna.archetype.BodyPlan;
import com.galacticodyssey.planet.BiomeType;

import java.util.Random;

public final class PaletteGenerator {

    private PaletteGenerator() {}

    public static CreatureSkinSpec generate(long colorSeed, BiomeType biome, BodyPlan bodyPlan) {
        Random rng = new Random(colorSeed);
        CreatureSkinSpec spec = new CreatureSkinSpec();

        float hueMin, hueMax, satMin, satMax, valMin, valMax;
        float[] patternWeights;
        switch (biome) {
            case DESERT: case SAVANNA: case ARID_SHRUB: case BADLANDS:
                hueMin = 20f; hueMax = 60f; satMin = 0.3f; satMax = 0.6f; valMin = 0.5f; valMax = 0.7f;
                patternWeights = new float[]{0.4f, 0.2f, 0.1f, 0f, 0.3f, 0f}; break;
            case TEMPERATE_FOREST: case BOREAL_FOREST: case TROPICAL_FOREST:
                hueMin = 80f; hueMax = 160f; satMin = 0.4f; satMax = 0.7f; valMin = 0.3f; valMax = 0.6f;
                patternWeights = new float[]{0f, 0.1f, 0.3f, 0.3f, 0.3f, 0f}; break;
            case TUNDRA: case ICE_SHEET: case POLAR_DESERT: case ICE_FIELD:
                hueMin = 0f; hueMax = 40f; satMin = 0.05f; satMax = 0.2f; valMin = 0.7f; valMax = 0.9f;
                patternWeights = new float[]{0.4f, 0.2f, 0.1f, 0f, 0.3f, 0f}; break;
            case OCEAN: case LAKE: case RIVER:
                hueMin = 180f; hueMax = 260f; satMin = 0.4f; satMax = 0.7f; valMin = 0.3f; valMax = 0.6f;
                patternWeights = new float[]{0f, 0.3f, 0.3f, 0f, 0.2f, 0.2f}; break;
            case VOLCANIC:
                hueMin = 0f; hueMax = 30f; satMin = 0.3f; satMax = 0.5f; valMin = 0.15f; valMax = 0.35f;
                patternWeights = new float[]{0.3f, 0.2f, 0.1f, 0f, 0.4f, 0f}; break;
            case SWAMP:
                hueMin = 60f; hueMax = 160f; satMin = 0.2f; satMax = 0.5f; valMin = 0.2f; valMax = 0.5f;
                patternWeights = new float[]{0.1f, 0.1f, 0.1f, 0f, 0.2f, 0.5f}; break;
            case GRASSLAND: case STEPPE: default:
                hueMin = 30f; hueMax = 120f; satMin = 0.3f; satMax = 0.6f; valMin = 0.4f; valMax = 0.7f;
                patternWeights = new float[]{0.1f, 0.4f, 0.2f, 0f, 0.3f, 0f}; break;
        }

        float hue = hueMin + rng.nextFloat() * (hueMax - hueMin);
        float sat = satMin + rng.nextFloat() * (satMax - satMin);
        float val = valMin + rng.nextFloat() * (valMax - valMin);
        float[] primary = hsvToRgb(hue, sat, val);
        spec.primaryR = primary[0]; spec.primaryG = primary[1]; spec.primaryB = primary[2];

        float hueShift = rng.nextBoolean() ? (30f + rng.nextFloat() * 30f) : (150f + rng.nextFloat() * 60f);
        float secHue = (hue + hueShift) % 360f;
        float[] secondary = hsvToRgb(secHue, sat * 0.9f, val * (0.8f + rng.nextFloat() * 0.4f));
        spec.secondaryR = secondary[0]; spec.secondaryG = secondary[1]; spec.secondaryB = secondary[2];

        float accHue = (hue + rng.nextFloat() * 60f - 30f + 360f) % 360f;
        float[] accent = hsvToRgb(accHue, 0.7f + rng.nextFloat() * 0.3f, 0.6f + rng.nextFloat() * 0.4f);
        spec.accentR = accent[0]; spec.accentG = accent[1]; spec.accentB = accent[2];

        float[] ventral = hsvToRgb(hue, sat * 0.5f, Math.min(1f, val * 1.3f));
        spec.ventralR = ventral[0]; spec.ventralG = ventral[1]; spec.ventralB = ventral[2];

        spec.patternType = pickPattern(rng, patternWeights);
        spec.patternScale = 0.5f + rng.nextFloat() * 2f;
        spec.patternContrast = 0.3f + rng.nextFloat() * 0.5f;

        if (spec.patternType == PatternType.BIOLUMINESCENT) {
            spec.bioGlow = 0.3f + rng.nextFloat() * 0.7f;
        }

        switch (bodyPlan) {
            case QUADRUPED: spec.roughness = 0.75f; spec.metallic = 0f; break;
            case BIPEDAL:   spec.roughness = 0.70f; spec.metallic = 0f; break;
            case HEXAPOD:   spec.roughness = 0.35f; spec.metallic = 0.15f; break;
            case SERPENTINE: spec.roughness = 0.45f; spec.metallic = 0.05f; break;
        }

        return spec;
    }

    private static PatternType pickPattern(Random rng, float[] weights) {
        float roll = rng.nextFloat();
        float cumulative = 0f;
        PatternType[] types = PatternType.values();
        for (int i = 0; i < weights.length && i < types.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return types[i];
        }
        return PatternType.SOLID;
    }

    static float[] hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        if      (h < 60f)  { r = c; g = x; b = 0; }
        else if (h < 120f) { r = x; g = c; b = 0; }
        else if (h < 180f) { r = 0; g = c; b = x; }
        else if (h < 240f) { r = 0; g = x; b = c; }
        else if (h < 300f) { r = x; g = 0; b = c; }
        else               { r = c; g = 0; b = x; }
        return new float[]{
            Math.max(0f, Math.min(1f, r + m)),
            Math.max(0f, Math.min(1f, g + m)),
            Math.max(0f, Math.min(1f, b + m))
        };
    }
}
