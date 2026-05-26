package com.galacticodyssey.ship;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipColorPaletteTest {

    @Test
    void sameSeedProducesSameColors() {
        ShipColorPalette a = new ShipColorPalette(42L);
        ShipColorPalette b = new ShipColorPalette(42L);
        assertEquals(a.baseColor,        b.baseColor);
        assertEquals(a.accentColor,      b.accentColor);
        assertEquals(a.trimColor,        b.trimColor);
        assertEquals(a.engineGlowColor,  b.engineGlowColor);
    }

    @Test
    void differentSeedsProduceDifferentColors() {
        ShipColorPalette a = new ShipColorPalette(1L);
        ShipColorPalette b = new ShipColorPalette(999L);
        boolean anyDifferent = !a.baseColor.equals(b.baseColor)
            || !a.accentColor.equals(b.accentColor)
            || !a.engineGlowColor.equals(b.engineGlowColor);
        assertTrue(anyDifferent, "Different seeds should produce at least one differing color");
    }

    @Test
    void colorsAreInValidRange() {
        ShipColorPalette p = new ShipColorPalette(42L);
        assertTrue(p.baseColor.r >= 0f && p.baseColor.r <= 1f, "baseColor.r out of range");
        assertTrue(p.baseColor.g >= 0f && p.baseColor.g <= 1f, "baseColor.g out of range");
        assertTrue(p.baseColor.b >= 0f && p.baseColor.b <= 1f, "baseColor.b out of range");
        assertTrue(p.accentColor.r >= 0f && p.accentColor.r <= 1f, "accentColor.r out of range");
        assertTrue(p.trimColor.r >= 0f && p.trimColor.r <= 1f, "trimColor.r out of range");
        assertTrue(p.engineGlowColor.r >= 0f && p.engineGlowColor.r <= 1f, "engineGlowColor.r out of range");
    }

    @Test
    void trimColorIsBlendBetweenBaseAndAccent() {
        ShipColorPalette p = new ShipColorPalette(77L);
        // trim = lerp(base, accent, 0.3)  =>  trim.r = base.r * 0.7 + accent.r * 0.3
        float expectedR = p.baseColor.r * 0.7f + p.accentColor.r * 0.3f;
        assertEquals(expectedR, p.trimColor.r, 0.001f, "trimColor.r should be 30% blend toward accent");
    }

    @Test
    void alphaIsAlwaysOne() {
        ShipColorPalette p = new ShipColorPalette(123L);
        assertEquals(1f, p.baseColor.a,       0.001f);
        assertEquals(1f, p.accentColor.a,     0.001f);
        assertEquals(1f, p.engineGlowColor.a, 0.001f);
    }
}
