package com.galacticodyssey.ship.weapons.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.weapons.data.ShipWeaponData;

public final class ShipWeaponFiredEvent {
    public final Entity shipEntity;
    public final String hardpointId;
    public final Vector3 origin;
    public final Vector3 direction;
    public final ShipWeaponData weaponData;

    public ShipWeaponFiredEvent(Entity shipEntity, String hardpointId,
                                Vector3 origin, Vector3 direction,
                                ShipWeaponData weaponData) {
        this.shipEntity = shipEntity;
        this.hardpointId = hardpointId;
        this.origin = origin;
        this.direction = direction;
        this.weaponData = weaponData;
    }
}
