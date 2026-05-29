package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

class ShipColorPaletteStyleTest {

    private static HullStyle singleColorStyle() {
        float[][] base = {{0.10f, 0.20f, 0.30f}};
        float[][] accent = {{0.40f, 0.50f, 0.60f}};
        float[][] glow = {{0.70f, 0.80f, 0.90f}};
        return new HullStyle("test", GeneratorType.LOFTED,
                2.0f, 3.0f, 0.8f, 1.2f, 1.0f, 0.015f,
                base, accent, glow, false);
    }

    @Test
    void picksColorsFromStylePalette() {
        ShipColorPalette p = new ShipColorPalette(1234L, singleColorStyle());
        assertEquals(new Color(0.10f, 0.20f, 0.30f, 1f), p.baseColor);
        assertEquals(new Color(0.40f, 0.50f, 0.60f, 1f), p.accentColor);
        assertEquals(new Color(0.70f, 0.80f, 0.90f, 1f), p.engineGlowColor);
    }

    @Test
    void deterministicForSameSeedAndStyle() {
        HullStyle s = HullStyle.defaultStyle();
        ShipColorPalette a = new ShipColorPalette(99L, s);
        ShipColorPalette b = new ShipColorPalette(99L, s);
        assertEquals(a.baseColor, b.baseColor);
        assertEquals(a.accentColor, b.accentColor);
        assertEquals(a.engineGlowColor, b.engineGlowColor);
    }

    @Test
    void legacyConstructorStillWorks() {
        ShipColorPalette p = new ShipColorPalette(7L);
        assertTrue(p.baseColor.a == 1f);
    }
}
