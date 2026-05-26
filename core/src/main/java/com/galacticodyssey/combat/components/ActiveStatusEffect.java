package com.galacticodyssey.combat.components;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public class ActiveStatusEffect {
    public final StatusEffectType type;
    public float remainingDuration;
    public final float tickRate;
    public final float magnitude;
    public float tickAccumulator;
    public final Entity source;
    public int stacks;

    public ActiveStatusEffect(StatusEffectType type, float duration, float tickRate,
                              float magnitude, Entity source) {
        this.type = type;
        this.remainingDuration = duration;
        this.tickRate = tickRate;
        this.magnitude = magnitude;
        this.source = source;
        this.stacks = 1;
    }
}
