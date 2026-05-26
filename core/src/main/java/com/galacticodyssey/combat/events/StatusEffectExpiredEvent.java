package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.StatusEffectType;

public final class StatusEffectExpiredEvent {
    public final Entity target;
    public final StatusEffectType effectType;

    public StatusEffectExpiredEvent(Entity target, StatusEffectType effectType) {
        this.target = target;
        this.effectType = effectType;
    }
}
