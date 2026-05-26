package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class WeaponFiredEvent {
    public final Entity shooter;
    public final Vector3 aimDirection;
    public final boolean hitscan;

    public WeaponFiredEvent(Entity shooter, Vector3 aimDirection, boolean hitscan) {
        this.shooter = shooter;
        this.aimDirection = new Vector3(aimDirection);
        this.hitscan = hitscan;
    }
}
