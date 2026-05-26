package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.WeaponSlot;

public final class WeaponSwitchedEvent {
    public final Entity entity;
    public final WeaponSlot oldSlot;
    public final WeaponSlot newSlot;

    public WeaponSwitchedEvent(Entity entity, WeaponSlot oldSlot, WeaponSlot newSlot) {
        this.entity = entity;
        this.oldSlot = oldSlot;
        this.newSlot = newSlot;
    }
}
