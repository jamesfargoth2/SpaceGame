package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.galacticodyssey.combat.CombatEnums.AttackDirection;

public final class MeleeBlockEvent {
    public final Entity attacker;
    public final Entity blocker;
    public final AttackDirection attackDirection;
    public final AttackDirection blockDirection;
    public final boolean perfectBlock;

    public MeleeBlockEvent(Entity attacker, Entity blocker, AttackDirection attackDirection,
                           AttackDirection blockDirection, boolean perfectBlock) {
        this.attacker = attacker;
        this.blocker = blocker;
        this.attackDirection = attackDirection;
        this.blockDirection = blockDirection;
        this.perfectBlock = perfectBlock;
    }
}
