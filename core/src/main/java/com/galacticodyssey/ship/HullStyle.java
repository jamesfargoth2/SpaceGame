package com.galacticodyssey.ship;

/**
 * Data-driven visual archetype for a faction's ships. Constrains the random
 * ranges the hull generator samples and supplies the color palette.
 *
 * <p>Colors are stored as RGB triples ({@code float[3]}, channels 0..1) for easy
 * JSON loading. {@link #defaultStyle()} replicates the legacy hardcoded constants
 * so generation is unchanged when no style is supplied.
 */
public final class HullStyle {

    public final String id;
    public final GeneratorType generatorType;

    // Lofted shape levers
    public final float sectionExponentMin;   // superellipse exponent (2=round, >2 boxy, <2 diamond)
    public final float sectionExponentMax;
    public final float aspectBiasMin;         // height/width bias
    public final float aspectBiasMax;
    public final float spineCurvature;        // 1.0 = legacy; >1 more curved, <1 straighter
    public final float panelInsetScale;       // recessed panel depth

    // Palette: each entry is {r, g, b} in 0..1
    public final float[][] baseColors;
    public final float[][] accentColors;
    public final float[][] glowColors;

    /** When true, the wear pass is skipped (ageless exotic hulls). */
    public final boolean ageless;

    public HullStyle(String id, GeneratorType generatorType,
                     float sectionExponentMin, float sectionExponentMax,
                     float aspectBiasMin, float aspectBiasMax,
                     float spineCurvature, float panelInsetScale,
                     float[][] baseColors, float[][] accentColors, float[][] glowColors,
                     boolean ageless) {
        this.id = id;
        this.generatorType = generatorType;
        this.sectionExponentMin = sectionExponentMin;
        this.sectionExponentMax = sectionExponentMax;
        this.aspectBiasMin = aspectBiasMin;
        this.aspectBiasMax = aspectBiasMax;
        this.spineCurvature = spineCurvature;
        this.panelInsetScale = panelInsetScale;
        this.baseColors = baseColors;
        this.accentColors = accentColors;
        this.glowColors = glowColors;
        this.ageless = ageless;
    }

    /** Replicates legacy hardcoded generation constants (no faction influence). */
    public static HullStyle defaultStyle() {
        float[][] base = {
            {0.6f, 0.6f, 0.62f}, {0.8f, 0.8f, 0.82f}, {0.2f, 0.25f, 0.35f},
            {0.25f, 0.32f, 0.22f}, {0.35f, 0.35f, 0.38f}, {0.45f, 0.42f, 0.4f},
        };
        float[][] accent = {
            {0.9f, 0.3f, 0.2f}, {0.2f, 0.5f, 0.9f}, {0.9f, 0.7f, 0.1f},
            {0.1f, 0.8f, 0.5f}, {0.8f, 0.4f, 0.0f}, {0.6f, 0.2f, 0.8f},
        };
        float[][] glow = {
            {0.3f, 0.5f, 1f}, {1f, 0.6f, 0.2f}, {0.9f, 0.9f, 1f},
        };
        return new HullStyle("default", GeneratorType.LOFTED,
                2.2f, 4.0f, 0.7f, 1.3f, 1.0f, 0.015f,
                base, accent, glow, false);
    }
}
