package com.galacticodyssey.ship;

import com.badlogic.gdx.graphics.Color;
import java.util.Random;

/**
 * Deterministic color palette for a procedural ship hull, sourced from a
 * {@link HullStyle}. The same seed + style always yields an identical palette.
 */
public class ShipColorPalette {

    /** Primary hull plate color. */
    public final Color baseColor;
    /** Panel accent stripe color. */
    public final Color accentColor;
    /** Inset panel trim — a blend of base toward accent. */
    public final Color trimColor;
    /** Engine nozzle / exhaust glow color (emissive). */
    public final Color engineGlowColor;

    /** Legacy constructor — uses {@link HullStyle#defaultStyle()}. */
    public ShipColorPalette(long seed) {
        this(seed, HullStyle.defaultStyle());
    }

    /** Constructs the palette deterministically from {@code seed} and {@code style}. */
    public ShipColorPalette(long seed, HullStyle style) {
        Random rng = new Random(seed);
        baseColor   = pick(style.baseColors,  rng);
        accentColor = pick(style.accentColors, rng);
        trimColor   = new Color(baseColor).lerp(accentColor, 0.3f);
        engineGlowColor = pick(style.glowColors, rng);
    }

    private static Color pick(float[][] colors, Random rng) {
        float[] c = colors[rng.nextInt(colors.length)];
        return new Color(c[0], c[1], c[2], 1f);
    }
}
