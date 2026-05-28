package com.galacticodyssey.combat.fleet.data;

public enum FleetDoctrine {
    AGGRESSIVE(0.6f, 0.40f, 1.2f, 1.2f),
    BALANCED(0.8f, 0.30f, 1.0f, 1.0f),
    DEFENSIVE(1.0f, 0.25f, 0.8f, 0.8f),
    EVASIVE(1.2f, 0.20f, 0.9f, 0.7f);

    public final float engageStrengthRatio;
    public final float retreatThreshold;
    public final float damageDealtModifier;
    public final float damageTakenModifier;

    FleetDoctrine(float engageStrengthRatio, float retreatThreshold,
                  float damageDealtModifier, float damageTakenModifier) {
        this.engageStrengthRatio = engageStrengthRatio;
        this.retreatThreshold = retreatThreshold;
        this.damageDealtModifier = damageDealtModifier;
        this.damageTakenModifier = damageTakenModifier;
    }
}
