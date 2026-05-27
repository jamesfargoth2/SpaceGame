package com.galacticodyssey.galaxy.anomaly;

/**
 * Environmental effects applied within an anomaly's hazard radius.
 */
public final class AnomalyEffects {

    public final float gravityMultiplier;
    public final float sensorRangeMultiplier;
    public final float shieldDrainRate;
    public final float radiationDoseRate;
    public final float jumpDriveStability;
    public final float physicsNoiseScale;

    public AnomalyEffects(float gravityMultiplier, float sensorRangeMultiplier,
                          float shieldDrainRate, float radiationDoseRate,
                          float jumpDriveStability, float physicsNoiseScale) {
        this.gravityMultiplier = gravityMultiplier;
        this.sensorRangeMultiplier = sensorRangeMultiplier;
        this.shieldDrainRate = shieldDrainRate;
        this.radiationDoseRate = radiationDoseRate;
        this.jumpDriveStability = jumpDriveStability;
        this.physicsNoiseScale = physicsNoiseScale;
    }

    /** Default: no environmental effects. */
    public static AnomalyEffects defaults() {
        return new AnomalyEffects(1f, 1f, 0f, 0f, 1f, 0f);
    }
}
