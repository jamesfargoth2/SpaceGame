package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

/**
 * Deterministic color palette for a procedural ship hull.
 *
 * <p>All four colors are derived from the same seed so the same seed always
 * yields an identical palette. Colors are copied from the palette arrays so
 * callers may mutate the returned instances freely.
 */
public class ShipColorPalette {

    private static final Color[] BASE_COLORS = {
        new Color(0.6f,  0.6f,  0.62f, 1f),
        new Color(0.8f,  0.8f,  0.82f, 1f),
        new Color(0.2f,  0.25f, 0.35f, 1f),
        new Color(0.25f, 0.32f, 0.22f, 1f),
        new Color(0.35f, 0.35f, 0.38f, 1f),
        new Color(0.45f, 0.42f, 0.4f,  1f),
    };

    private static final Color[] ACCENT_COLORS = {
        new Color(0.9f, 0.3f, 0.2f, 1f),
        new Color(0.2f, 0.5f, 0.9f, 1f),
        new Color(0.9f, 0.7f, 0.1f, 1f),
        new Color(0.1f, 0.8f, 0.5f, 1f),
        new Color(0.8f, 0.4f, 0.0f, 1f),
        new Color(0.6f, 0.2f, 0.8f, 1f),
    };

    /** Primary hull plate color. */
    public final Color baseColor;
    /** Panel accent stripe color. */
    public final Color accentColor;
    /** Inset panel trim — a blend of base toward accent. */
    public final Color trimColor;
    /** Engine nozzle / exhaust glow color (emissive). */
    public final Color engineGlowColor;

    /**
     * Constructs the palette deterministically from {@code seed}.
     *
     * @param seed ship seed; the same value as {@link ShipBlueprint#seed}
     */
    public ShipColorPalette(long seed) {
        Random rng = new Random(seed);
        baseColor   = new Color(BASE_COLORS  [rng.nextInt(BASE_COLORS.length)]);
        accentColor = new Color(ACCENT_COLORS[rng.nextInt(ACCENT_COLORS.length)]);
        trimColor   = new Color(baseColor).lerp(accentColor, 0.3f);

        float glowHue = rng.nextFloat();
        if      (glowHue < 0.33f) engineGlowColor = new Color(0.3f, 0.5f, 1f,  1f);
        else if (glowHue < 0.66f) engineGlowColor = new Color(1f,   0.6f, 0.2f, 1f);
        else                      engineGlowColor = new Color(0.9f, 0.9f, 1f,  1f);
    }
}
