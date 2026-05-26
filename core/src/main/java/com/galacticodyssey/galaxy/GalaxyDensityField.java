package com.galacticodyssey.galaxy;

import com.badlogic.gdx.math.MathUtils;

public class GalaxyDensityField {

    public float density(float nx, float ny, GalaxyConfig cfg, GalaxyNoise noise) {
        float r = (float) Math.sqrt(nx * nx + ny * ny);
        if (r > 1f) return 0f;

        float angle = MathUtils.atan2(ny, nx);
        float d = spiralDensity(r, angle, cfg);

        float coreBulge = cfg.coreDensityFactor * (float) Math.exp(-r * 4f);
        d = Math.max(0f, d + coreBulge);

        d *= 0.85f + 0.3f * noise.fbm(nx * 3f, ny * 3f, 3, 0.5f, 2f);

        return Math.min(Math.max(d, 0f), 1f);
    }

    private float spiralDensity(float r, float angle, GalaxyConfig cfg) {
        // Phase shift so arm 0 passes through angle=0 at r=0.5 (reference radius),
        // ensuring the on-axis test point (0.5, 0) lands on an arm.
        float phaseRef = cfg.armWindingAngle * (float) Math.log(0.5f + 0.1f);
        float maxDensity = 0f;
        for (int arm = 0; arm < cfg.armCount; arm++) {
            float armOffset = arm * (MathUtils.PI2 / cfg.armCount);
            float spiralAngle = cfg.armWindingAngle * (float) Math.log(r + 0.1f) - phaseRef + armOffset;
            float angleDiff = Math.abs(normaliseAngle(angle - spiralAngle));
            float armDensity = (float) Math.exp(
                -angleDiff * angleDiff / (2f * cfg.armWidth * cfg.armWidth));
            armDensity *= (1f - r * 0.7f);
            maxDensity = Math.max(maxDensity, armDensity);
        }
        return maxDensity;
    }

    private float normaliseAngle(float a) {
        while (a > MathUtils.PI) a -= MathUtils.PI2;
        while (a < -MathUtils.PI) a += MathUtils.PI2;
        return a;
    }
}
