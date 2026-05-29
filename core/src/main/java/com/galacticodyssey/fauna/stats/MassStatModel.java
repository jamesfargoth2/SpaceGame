package com.galacticodyssey.fauna.stats;

import com.galacticodyssey.fauna.archetype.BodyPlanArchetypeDef;

/** Derives combat stats from creature mass using allometric scaling. */
public final class MassStatModel {

    public float mass(float volume, float density) { return volume * density; }

    /** Returns {maxHP, moveSpeed, meleeDamage}, clamped to sane ranges. */
    public float[] deriveStats(float mass, BodyPlanArchetypeDef a) {
        float hp    = clamp(a.kHp     * (float) Math.pow(mass,  2.0 / 3.0), 1f, 100000f);
        float speed = clamp(a.kSpeed  * (float) Math.pow(mass, -0.25),      0.5f, 30f);
        float dmg   = clamp(a.kDamage * (float) Math.pow(mass,  0.75),      1f, 10000f);
        return new float[]{hp, speed, dmg};
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
