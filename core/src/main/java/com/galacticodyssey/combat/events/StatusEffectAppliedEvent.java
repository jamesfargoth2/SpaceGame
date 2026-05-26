package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public final class StatusEffectAppliedEvent {
    public final Entity target;
    public final StatusEffectType effectType;
    public final Entity source;

    public StatusEffectAppliedEvent(Entity target, StatusEffectType effectType, Entity source) {
        this.target = target;
        this.effectType = effectType;
        this.source = source;
    }
}
