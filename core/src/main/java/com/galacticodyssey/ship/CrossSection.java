package com.galacticodyssey.ship;

import com.badlogic.gdx.math.MathUtils;

/**
 * Generates a 2-D vertex ring using a superellipse (Lamé curve) parametrisation.
 *
 * <p>The superellipse equation is {@code |x/a|^n + |y/b|^n = 1}.
 * <ul>
 *   <li>n = 2 → standard ellipse</li>
 *   <li>n &gt; 2 → rounded rectangle (boxy)</li>
 *   <li>n &lt; 2 → diamond / astroid</li>
 * </ul>
 *
 * <p>The parametric form used here:
 * <pre>
 *   x(θ) = sign(cos θ) · halfWidth  · |cos θ|^(2/n)
 *   y(θ) = sign(sin θ) · halfHeight · |sin θ|^(2/n)
 * </pre>
 */
public class CrossSection {

    private final float halfWidth;
    private final float halfHeight;
    private final float exponent;

    /**
     * @param halfWidth  half-extent along the X axis (a)
     * @param halfHeight half-extent along the Y axis (b)
     * @param exponent   superellipse exponent n (n=2 → ellipse, n&gt;2 → boxy)
     */
    public CrossSection(float halfWidth, float halfHeight, float exponent) {
        this.halfWidth = halfWidth;
        this.halfHeight = halfHeight;
        this.exponent = exponent;
    }

    /**
     * Generates a ring of {@code vertexCount} evenly-spaced 2-D vertices.
     *
     * @param vertexCount number of vertices; must be &gt; 0
     * @return array of [x, y] pairs, one per vertex, in counter-clockwise order
     */
    public float[][] generateRing(int vertexCount) {
        float[][] ring = new float[vertexCount][2];
        float exp = 2f / exponent;

        for (int i = 0; i < vertexCount; i++) {
            float angle = MathUtils.PI2 * i / vertexCount;
            float cosA = MathUtils.cos(angle);
            float sinA = MathUtils.sin(angle);

            float signX = Math.signum(cosA);
            float signY = Math.signum(sinA);

            ring[i][0] = signX * halfWidth  * (float) Math.pow(Math.abs(cosA), exp);
            ring[i][1] = signY * halfHeight * (float) Math.pow(Math.abs(sinA), exp);
        }
        return ring;
    }

    /** @return half-extent along the X axis */
    public float getHalfWidth()  { return halfWidth; }

    /** @return half-extent along the Y axis */
    public float getHalfHeight() { return halfHeight; }

    /** @return superellipse exponent */
    public float getExponent()   { return exponent; }
}
