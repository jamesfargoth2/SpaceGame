package com.galacticodyssey.combat.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;

public final class WeaponFiredEvent {
    public final Entity shooter;
    public final Vector3 aimDirection;
    public final boolean hitscan;
    /** World-space muzzle tip position — projectiles and VFX spawn here. */
    public final Vector3 muzzlePosition;

    public WeaponFiredEvent(Entity shooter, Vector3 aimDirection, boolean hitscan, Vector3 muzzlePosition) {
        this.shooter = shooter;
        this.aimDirection = new Vector3(aimDirection);
        this.hitscan = hitscan;
        this.muzzlePosition = new Vector3(muzzlePosition);
    }
}
