package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class HullStyleTest {

    @Test
    void generatorTypeHasLoftedAndFaceted() {
        assertEquals(2, GeneratorType.values().length);
        assertEquals(GeneratorType.LOFTED, GeneratorType.valueOf("LOFTED"));
        assertEquals(GeneratorType.FACETED, GeneratorType.valueOf("FACETED"));
    }

    @Test
    void shipRoleHasFiveValues() {
        assertEquals(5, ShipRole.values().length);
        assertEquals(ShipRole.WARSHIP, ShipRole.valueOf("WARSHIP"));
        assertEquals(ShipRole.CIVILIAN, ShipRole.valueOf("CIVILIAN"));
    }

    @Test
    void defaultStyleReplicatesCurrentConstants() {
        HullStyle s = HullStyle.defaultStyle();
        assertEquals(GeneratorType.LOFTED, s.generatorType);
        assertEquals(2.2f, s.sectionExponentMin, 1e-4);
        assertEquals(4.0f, s.sectionExponentMax, 1e-4);
        assertEquals(0.7f, s.aspectBiasMin, 1e-4);
        assertEquals(1.3f, s.aspectBiasMax, 1e-4);
        assertEquals(1.0f, s.spineCurvature, 1e-4);
        assertEquals(0.015f, s.panelInsetScale, 1e-4);
        assertFalse(s.ageless);
    }

    @Test
    void defaultStyleHasNonEmptyPalettes() {
        HullStyle s = HullStyle.defaultStyle();
        assertTrue(s.baseColors.length > 0);
        assertTrue(s.accentColors.length > 0);
        assertTrue(s.glowColors.length > 0);
        assertEquals(3, s.baseColors[0].length);
    }

    @Test
    void constructorStoresFields() {
        float[][] base = {{0.1f, 0.2f, 0.3f}};
        float[][] accent = {{0.4f, 0.5f, 0.6f}};
        float[][] glow = {{0.7f, 0.8f, 0.9f}};
        HullStyle s = new HullStyle("vaun", GeneratorType.LOFTED,
                3.5f, 4.5f, 0.8f, 1.0f, 0.4f, 0.03f,
                base, accent, glow, true);
        assertEquals("vaun", s.id);
        assertEquals(3.5f, s.sectionExponentMin, 1e-4);
        assertTrue(s.ageless);
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, s.baseColors[0], 1e-4f);
    }
}
