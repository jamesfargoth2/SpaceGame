package com.galacticodyssey.planet.cave;

/** A glowing bioluminescent patch within a cave chamber. */
public final class BioluminescentPatch {
    public final float cx, cy, cz;
    public final float radius;
    public final float colorR, colorG, colorB;
    /** Pulse frequency in Hz. */
    public final float pulseSpeed;

    public BioluminescentPatch(float cx, float cy, float cz, float radius,
                               float colorR, float colorG, float colorB,
                               float pulseSpeed) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.colorR = colorR;
        this.colorG = colorG;
        this.colorB = colorB;
        this.pulseSpeed = pulseSpeed;
    }
}
