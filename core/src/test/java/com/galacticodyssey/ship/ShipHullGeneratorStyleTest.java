package com.galacticodyssey.ship;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShipHullGeneratorStyleTest {

    private static HullStyle boxyStyle() {
        float[][] base = {{0.5f, 0.5f, 0.5f}};
        float[][] accent = {{0.9f, 0.3f, 0.2f}};
        float[][] glow = {{1f, 0.6f, 0.2f}};
        // high exponent => boxy; low spine curvature => straight
        return new HullStyle("boxy", GeneratorType.LOFTED,
                3.8f, 4.2f, 0.8f, 1.0f, 0.2f, 0.03f,
                base, accent, glow, false);
    }

    private static HullStyle roundStyle() {
        float[][] base = {{0.5f, 0.5f, 0.5f}};
        float[][] accent = {{0.2f, 0.5f, 0.9f}};
        float[][] glow = {{0.3f, 0.5f, 1f}};
        // low exponent => round; high spine curvature => curvy
        return new HullStyle("round", GeneratorType.LOFTED,
                1.8f, 2.2f, 0.8f, 1.0f, 2.0f, 0.01f,
                base, accent, glow, false);
    }

    @Test
    void sameSeedAndStyleIsDeterministic() {
        ShipBlueprint bp = new ShipBlueprint(4242L, ShipSizeClass.SMALL);
        ShipHullGenerator g = new ShipHullGenerator();
        HullGeometry a = g.generate(bp, boxyStyle());
        HullGeometry b = g.generate(bp, boxyStyle());
        org.junit.jupiter.api.Assertions.assertArrayEquals(a.vertices, b.vertices, 0f);
    }

    @Test
    void boxyStyleIsWiderThanRoundAtSameSeed() {
        // Higher exponent fills the bounding cross-section more, so for the same
        // blueprint the boxy hull's averaged |x| extent exceeds the round one.
        ShipBlueprint bp = new ShipBlueprint(2024L, ShipSizeClass.SMALL);
        ShipHullGenerator g = new ShipHullGenerator();
        float boxyFill = averageAbsX(g.generate(bp, boxyStyle()));
        float roundFill = averageAbsX(g.generate(bp, roundStyle()));
        assertTrue(boxyFill > roundFill,
                "boxy avg|x|=" + boxyFill + " should exceed round avg|x|=" + roundFill);
    }

    private static float averageAbsX(HullGeometry hull) {
        int stride = hull.vertexStride;
        double sum = 0;
        int n = hull.vertexCount();
        for (int i = 0; i < n; i++) sum += Math.abs(hull.vertices[i * stride]);
        return (float) (sum / n);
    }
}
