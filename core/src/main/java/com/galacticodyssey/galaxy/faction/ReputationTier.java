package com.galacticodyssey.galaxy.faction;

public enum ReputationTier {

    HOSTILE    (-100f, -50f,  0f,    0f),
    UNFRIENDLY( -50f,   0f,  1.20f, 0.80f),
    NEUTRAL   (   0f,  25f,  1.00f, 1.00f),
    FRIENDLY  (  25f,  50f,  0.95f, 1.05f),
    ALLIED    (  50f,  75f,  0.90f, 1.10f),
    HONORED   (  75f, 100f,  0.85f, 1.15f),
    EXALTED   ( 100f, 100f,  0.80f, 1.20f);

    public final float minInclusive;
    public final float maxExclusive;
    public final float buyMultiplier;
    public final float sellMultiplier;

    ReputationTier(float minInclusive, float maxExclusive,
                   float buyMultiplier, float sellMultiplier) {
        this.minInclusive  = minInclusive;
        this.maxExclusive  = maxExclusive;
        this.buyMultiplier = buyMultiplier;
        this.sellMultiplier = sellMultiplier;
    }

    /** Maps a standing value in [-100, +100] to the corresponding tier. Values outside the
     *  range are clamped before lookup. */
    public static ReputationTier fromStanding(float standing) {
        float clamped = Math.max(-100f, Math.min(100f, standing));
        if (clamped >= 100f) return EXALTED;
        if (clamped >=  75f) return HONORED;
        if (clamped >=  50f) return ALLIED;
        if (clamped >=  25f) return FRIENDLY;
        if (clamped >=   0f) return NEUTRAL;
        if (clamped >= -50f) return UNFRIENDLY;
        return HOSTILE;
    }
}
