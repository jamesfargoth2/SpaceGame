package com.galacticodyssey.ship.boarding.events;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector3;
import com.galacticodyssey.ship.boarding.BoardingOperationComponent.AttachMethod;

/** A boarding attach completed: a hard connection (clamp or breach pod) opened an entry point. */
public final class ShipBreachedEvent {
    public final Entity aggressor;
    public final Entity target;
    public final AttachMethod method;
    /** Entry spawn point in TARGET-ship-local coordinates. */
    public final Vector3 entryLocalPosition;

    public ShipBreachedEvent(Entity aggressor, Entity target, AttachMethod method, Vector3 entryLocalPosition) {
        this.aggressor = aggressor;
        this.target = target;
        this.method = method;
        this.entryLocalPosition = new Vector3(entryLocalPosition);
    }
}
